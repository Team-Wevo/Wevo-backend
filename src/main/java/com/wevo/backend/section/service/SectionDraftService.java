package com.wevo.backend.section.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.review.service.ReviewLinkService;
import com.wevo.backend.review.service.TeamReviewService;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.SectionDraft;
import com.wevo.backend.section.dto.request.SectionDraftSaveRequest;
import com.wevo.backend.section.dto.response.SectionDraftEvidenceResponse;
import com.wevo.backend.section.dto.response.SectionDraftReadResponse;
import com.wevo.backend.section.dto.response.SectionDraftReadResponse.ActiveEditor;
import com.wevo.backend.section.dto.response.SectionDraftSaveResponse;
import com.wevo.backend.section.dto.response.SectionDraftVersionDetailResponse;
import com.wevo.backend.section.dto.response.SectionDraftVersionListResponse;
import com.wevo.backend.section.repository.SectionDraftRepository;
import com.wevo.backend.user.service.UserService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 섹션 초안(본문) 저장 로직. (제품 정책서 §6.1)
 *
 * <p>초안은 <b>버전별 이력</b>으로 관리한다 — 저장할 때마다 새 {@link SectionDraft} 행을
 * {@code version = 직전 최신 + 1} 로 append 한다. 확정 본문 버전 추적·검토 만료 판단의 근거가 된다.
 *
 * <p>본문이 바뀌면 이 섹션의 기존 검토는 더 이상 유효하지 않으므로, 저장의 <b>부수효과</b>로
 * AI 사전 검토(aiCheckStatus)·팀 검토·외부 검토 링크를 모두 만료 처리한다.
 * (§5.2.3 · §6.1 — 새 본문으로 다시 검토받아야 함)
 * 반대로 <b>본문이 직전 버전과 같으면 저장을 무시</b>한다 — 버전도 올리지 않고 만료도 하지 않는다.
 *
 * <p>기존 초안 수정은 편집권(lease) 보유자만 가능하다 (§5.2.1) — 타인이 편집 중이면 S004,
 * 미보유·만료면 S005 로 거부한다. 최초 저장(초안 없음)은 예외다.
 * 새 버전이 실제로 저장되면 편집 라운드가 끝난 것으로 보고 편집권을 해제한다 (§5.2.1) —
 * 멱등(본문 동일) 저장은 저장을 건너뛰므로 해제하지 않는다.
 *
 * <p>같은 섹션에 대한 동시 저장은 섹션 행 배타 잠금으로 직렬화하고, 클라이언트가 편집을 시작한
 * 기준 버전({@code baseVersion})이 최신과 다르면 409 로 거부해 덮어쓰기(lost update)를 막는다.
 */
@Service
@Transactional(readOnly = true)
public class SectionDraftService {

    /** 시간 값은 배포 서버 시간대와 무관하게 KST로 고정한다. (CLAUDE.md §5.4) */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final SectionAccessGuard sectionAccessGuard;
    private final SectionDraftRepository sectionDraftRepository;
    private final UserService userService;
    private final ReviewLinkService reviewLinkService;
    private final TeamReviewService teamReviewService;
    private final DraftLeaseService draftLeaseService;
    private final SectionDriftService sectionDriftService;
    private final SectionDraftEvidenceQueryService sectionDraftEvidenceQueryService;

    public SectionDraftService(SectionAccessGuard sectionAccessGuard,
                               SectionDraftRepository sectionDraftRepository,
                               UserService userService,
                               ReviewLinkService reviewLinkService,
                               TeamReviewService teamReviewService,
                               DraftLeaseService draftLeaseService,
                               SectionDriftService sectionDriftService,
                               SectionDraftEvidenceQueryService sectionDraftEvidenceQueryService) {
        this.sectionAccessGuard = sectionAccessGuard;
        this.sectionDraftRepository = sectionDraftRepository;
        this.userService = userService;
        this.reviewLinkService = reviewLinkService;
        this.teamReviewService = teamReviewService;
        this.draftLeaseService = draftLeaseService;
        this.sectionDriftService = sectionDriftService;
        this.sectionDraftEvidenceQueryService = sectionDraftEvidenceQueryService;
    }

    /**
     * 섹션의 최신 초안을 조회한다. (프로젝트 참여자 전용 )
     *
     * <p>편집 화면의 진입점이다. 응답의 {@code contentVersion} 은 이어지는 저장의
     * {@code baseVersion} 으로 쓰인다.
     *
     * <p>현재 편집권을 보유한 사용자가 있으면 {@code activeEditor} 로 함께 내려 "OO님 편집 중"을
     * 표시할 수 있게 한다. 만료된 편집권은 보유자로 보지 않는다({@link DraftLeaseService} 가 판정).
     *
     * @throws BusinessException 섹션 없음/미참여(존재 숨김, {@code S001}), 초안 미존재({@code S003})
     */
    public SectionDraftReadResponse getLatestDraft(Long sectionId, Long userId) {
        sectionAccessGuard.requireParticipantSection(sectionId, userId);

        SectionDraft draft = sectionDraftRepository
                .findTopByProjectSection_IdOrderByVersionDesc(sectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SECTION_DRAFT_NOT_FOUND));

        return SectionDraftReadResponse.of(draft, findActiveEditor(sectionId));
    }

    /**
     * 섹션 초안의 버전 이력을 조회한다. (본문 제외 — API_SPEC §3.7.8, 프로젝트 참여자 전용)
     *
     * <p>초안 저장이 본문 전체를 덮어쓰는 구조라, 실수로 지운 내용은 최신 본문만 보이는 화면에서
     * 되찾을 수 없다. 지난 버전을 <b>고를 수 있게</b> 메타데이터만 내리고, 본문은
     * {@link #getDraftVersion} 으로 한 건씩 받는다. <b>되돌리기(롤백)는 제공하지 않는다</b> —
     * 사용자가 필요한 대목을 직접 복사해 붙이는 것이 복구 경로다.
     *
     * <p><b>버전이 하나도 없어도 오류가 아니다</b> — 빈 목록으로 응답한다. 초안 미존재를
     * {@code S003} 으로 막는 최신 초안 조회(§3.7.1)와 다른데, 그쪽은 "편집할 본문"을 요구하는
     * 진입점이라 없으면 화면이 성립하지 않지만, 이력은 "아직 저장된 적 없음"이 정상 상태이기
     * 때문이다.
     *
     * <p><b>페이지네이션은 두지 않는다</b> — 한 섹션의 버전 수는 그 섹션을 저장한 횟수이고, MVP
     * 규모(4인 팀 · 섹션 6개)에서 한 화면에 담기지 않을 만큼 쌓이지 않는다. 내 프로젝트 목록
     * (§3.2.2)과 같은 판단이며, 필요해지면 §1.5 공통 계약으로 추가하는 계약 변경으로 다룬다.
     *
     * @throws BusinessException 섹션 없음/미참여(존재 숨김, {@code S001})
     */
    public SectionDraftVersionListResponse getDraftVersions(Long sectionId, Long userId) {
        sectionAccessGuard.requireParticipantSection(sectionId, userId);

        return SectionDraftVersionListResponse.from(
                sectionDraftRepository.findVersionHistoryBySectionId(sectionId));
    }

    /**
     * 섹션 초안의 특정 버전 본문을 조회한다. (API_SPEC §3.7.9, 프로젝트 참여자 전용)
     *
     * <p>이력 목록(§3.7.8)에서 고른 버전을 열어보는 용도다. 읽기 전용이라 편집권을 요구하지도,
     * 발급하지도 않는다 — 지난 본문을 읽는 것은 다른 사람의 편집을 방해하지 않는다.
     *
     * <p>없는 버전 요청은 초안 미존재와 같은 {@code S003} 으로 응답한다. 클라이언트가 두 경우를
     * 나눠 행동할 일이 없어(둘 다 "그 본문은 없다") 전용 코드를 만들지 않는다. (CLAUDE.md §5.8)
     *
     * @throws BusinessException 섹션 없음/미참여(존재 숨김, {@code S001}),
     *                           해당 버전 없음({@code S003})
     */
    public SectionDraftVersionDetailResponse getDraftVersion(Long sectionId, Long userId, Integer version) {
        sectionAccessGuard.requireParticipantSection(sectionId, userId);

        SectionDraft draft = sectionDraftRepository
                .findVersionWithEditor(sectionId, version)
                .orElseThrow(() -> new BusinessException(ErrorCode.SECTION_DRAFT_NOT_FOUND));

        return SectionDraftVersionDetailResponse.from(draft);
    }

    /**
     * 최신 AI 초안의 근거(사용된 의견·합의점·쟁점 결정·보충 근거)를 조회한다. (API_SPEC §3.7.3)
     *
     * <p>초안 <b>생성 시점</b>에 고정된 스냅샷을 그대로 반환한다 — 이후 정리 세트가 대체돼도
     * (§3.8.1) 이 초안이 실제로 근거로 삼은 내용은 바뀌지 않는다.
     *
     * @throws BusinessException 섹션 없음/미참여(존재 숨김, {@code S001}),
     *                           AI로 생성된 초안이 없음({@code S003})
     */
    public SectionDraftEvidenceResponse getDraftEvidence(Long sectionId, Long userId) {
        VerifiedProjectAccess access =
                sectionAccessGuard.requireParticipantAccessForSection(sectionId, userId);
        return SectionDraftEvidenceResponse.of(
                sectionDraftEvidenceQueryService.getLatest(access, sectionId));
    }

    /**
     * 현재 편집권 보유자를 찾는다. 아무도 없거나 만료됐으면 {@code null}.
     *
     * <p>참여자 검증은 호출측에서 이미 마쳤으므로, 권한을 다시 확인하는 {@code getStatus} 대신
     * 조회만 하는 {@link DraftLeaseService#findActiveLease} 를 쓴다.
     */
    private ActiveEditor findActiveEditor(Long sectionId) {
        return draftLeaseService.findActiveLease(sectionId)
                .map(lease -> new ActiveEditor(
                        lease.getHolderUserId(),
                        userService.getUserName(lease.getHolderUserId())))
                .orElse(null);
    }

    /**
     * 섹션 초안을 저장한다. (프로젝트 참여자 전용)
     *
     * <p>섹션 행을 배타 잠금으로 잡아 "최신 버전 조회 → append" 를 직렬화한다. 같은 잠금을
     * 만료 처리(외부 링크·팀 검토)도 사용하므로, 저장과 만료가 원자적으로 커밋된다.
     *
     * @throws BusinessException 섹션 없음/미참여(존재 숨김 규칙, {@code S001}), 초안이 없는
     *                           단계에서의 저장 시도({@code S002}), 타인이 편집 중({@code S004}),
     *                           편집권 미보유·만료({@code S005}), 기준 버전 불일치({@code C003})
     */
    @Transactional
    public SectionDraftSaveResponse saveDraft(Long sectionId, Long userId, SectionDraftSaveRequest request) {
        ProjectSection section = sectionAccessGuard.requireParticipantSectionForUpdate(sectionId, userId);

        if (!section.getStatus().allowsDraftEditing()) {
            throw new BusinessException(ErrorCode.INVALID_SECTION_STATUS_TRANSITION);
        }

        Optional<SectionDraft> latest =
                sectionDraftRepository.findTopByProjectSection_IdOrderByVersionDesc(sectionId);
        // 초안이 없으면(최초 저장) 최신 버전 0 으로 본다(→ version 1).
        int latestVersion = latest.map(SectionDraft::getVersion).orElse(0);

        // 초안을 수정하려면 편집권(lease) 보유자여야 한다 (§5.2.1) — 타인이 편집 중이면 S004, 본인이 미보유(없음·만료)면 S005.
        // 편집권 검사를 기준 버전 충돌보다 먼저 두어 "편집 권한 없음"을 우선 알린다. 최초 저장(초안 없음)은 보호할 기존 본문이 없고
        // lease 획득도 초안 존재를 전제로 하므로 예외로 둔다.
        if (latest.isPresent()) {
            draftLeaseService.requireActiveHolder(sectionId, userId);
        }

        //버전 충돌 검사
        if (!request.baseVersion().equals(latestVersion)) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        // 본문이 그대로면 새 버전을 만들지 않고 현재 버전을 그대로 돌려준다(멱등).
        if (latest.isPresent() && Objects.equals(latest.get().getContent(), request.content())) {
            return SectionDraftSaveResponse.from(latest.get(), section.getStatus(), List.of());
        }

        SectionDraft draft = SectionDraft.builder()
                .projectSection(section)
                .content(request.content())
                .version(latestVersion + 1)
                .lastEditor(userService.getUserReference(userId))
                .build();
        try {
            // auditing(updatedAt)을 응답에 싣기 위해 저장 후 flush 한다.
            sectionDraftRepository.saveAndFlush(draft);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        // 사람이 본문을 고친 시점이므로 목록 카드가 가리키는 "마지막 작업 지점"을 여기로 옮긴다.
        // (API_SPEC §3.2.2) 본문이 그대로인 멱등 저장은 위에서 이미 반환됐으므로 여기까지 오지 않는다.
        section.recordActivity(LocalDateTime.now(KST));

        // 본문이 바뀌었으므로 이 섹션의 기존 검토 결과를 같은 트랜잭션에서 모두 만료 처리한다 (§5.2.3).
        // AI 사전 검토(aiCheckStatus)는 성공한 검토가 있을 때(CURRENT)만 OUTDATED로 내려가며,
        // 검토 이력이 없으면(null) 그대로 둔다 — ProjectSection#markAiCheckOutdated 가 판정한다.
        section.markAiCheckOutdated();
        reviewLinkService.markSectionLinksOutdated(sectionId);
        teamReviewService.markSectionTeamReviewsOutdated(sectionId);
        var driftedSections = sectionDriftService.propagateConfirmedContentChange(
                section, userId, draft.getVersion());

        // 저장으로 편집 라운드가 끝났으므로 편집권을 해제한다 (§5.2.1) — 다른 멤버가 즉시 편집할 수 있다.
        // 멱등(본문 동일) 저장은 위에서 이미 반환해 여기 오지 않으므로, 실제 새 버전 저장 시에만 해제된다.
        // 최초 저장(초안 없음)은 보유한 편집권이 없어 해제 대상이 아니다.
        if (latest.isPresent()) {
            draftLeaseService.releaseHeldBy(sectionId, userId);
        }

        return SectionDraftSaveResponse.from(draft, section.getStatus(), driftedSections);
    }
}
