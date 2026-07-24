package com.wevo.backend.section.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.response.FieldError;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.review.service.ConfirmReviewGate;
import com.wevo.backend.review.service.TeamReviewService;
import com.wevo.backend.section.domain.AiCheckStatus;
import com.wevo.backend.section.domain.ConfirmReadinessCheck;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.domain.SectionDraft;
import com.wevo.backend.section.dto.response.SectionConfirmResponse;
import com.wevo.backend.section.repository.SectionDraftRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 섹션 확정 — 검토가 끝난 섹션을 확정 단계로 보낸다. (정책서 §6.3)
 *
 * <p>확정 <b>가능 여부 조회</b>({@link SectionConfirmReadinessService})가 버튼 활성화를 위한 조회라면,
 * 이 서비스는 <b>실제 확정 실행</b>이다 — 같은 §6.3 조건을 서버에서 재검증한 뒤 상태를 전이한다.
 * 조회와 실행 사이에 조건이 바뀔 수 있으므로(TOCTOU) 실행 시점에 반드시 다시 검증한다.
 *
 * <ul>
 *   <li><b>권한</b>: OWNER만 (§6.3 조건 5, §6.3.2 "팀장만 섹션을 확정할 수 있습니다")</li>
 *   <li><b>상태 게이트</b>: 섹션이 {@code REVIEWING}이 아니면 전이 불가로 보고 {@code S002}로 거부한다.</li>
 *   <li><b>조건 재검증</b>: AI 사전 검토 {@code CURRENT} · 팀원 1명 이상 동의 · 미해결 수정요청 0건 ·
 *       활성 편집자 없음. 미충족이면 조건별 사유와 함께 {@code C003}으로 모아 거부한다(§6.3.2).</li>
 * </ul>
 *
 * <p>전이·이력·확정 부수효과(확정 본문 버전 기록·드리프트 해소)는 section 도메인 단일 진입점
 * ({@link SectionStatusService#markConfirmed})에 위임한다 — 이 서비스는 진입 조건 검증과 조합만 한다.
 */
@Service
@Transactional(readOnly = true)
public class SectionConfirmService {

    private final SectionAccessGuard sectionAccessGuard;
    private final SectionDraftRepository sectionDraftRepository;
    private final TeamReviewService teamReviewService;
    private final DraftLeaseService draftLeaseService;
    private final SectionStatusService sectionStatusService;

    public SectionConfirmService(SectionAccessGuard sectionAccessGuard,
                                 SectionDraftRepository sectionDraftRepository,
                                 TeamReviewService teamReviewService,
                                 DraftLeaseService draftLeaseService,
                                 SectionStatusService sectionStatusService) {
        this.sectionAccessGuard = sectionAccessGuard;
        this.sectionDraftRepository = sectionDraftRepository;
        this.teamReviewService = teamReviewService;
        this.draftLeaseService = draftLeaseService;
        this.sectionStatusService = sectionStatusService;
    }

    /**
     * 섹션을 확정한다. (팀장 전용)
     *
     * <p>섹션 행을 배타 잠금으로 잡아 초안 저장·검토 요청·lease 획득과 직렬화한다 — 조건 검사와 전이
     * 사이에 다른 요청(편집 잠금 획득 등)이 끼어들지 못하게 해, 방금 편집을 시작한 섹션이 확정되는
     * 경합을 막는다.
     *
     * @throws BusinessException 섹션 없음/비멤버(존재 숨김 {@code S001}), OWNER 아님({@code A002}),
     *                           섹션이 {@code REVIEWING}이 아님({@code S002}),
     *                           §6.3 조건 미충족({@code C003} — {@code errors[]}에 조건별 사유)
     */
    @Transactional
    public SectionConfirmResponse confirm(Long sectionId, Long userId) {
        ProjectSection section = sectionAccessGuard.requireOwnedSectionForUpdate(sectionId, userId);

        // 상태 게이트(§6.3.2 SECTION_REVIEWING)는 조건 미충족(C003)이 아니라 전이 불가(S002)로 분리한다.
        if (section.getStatus() != ProjectSectionStatus.REVIEWING) {
            throw new BusinessException(ErrorCode.INVALID_SECTION_STATUS_TRANSITION);
        }

        // 나머지 §6.3 조건을 서버에서 재검증한다. 미충족 조건은 조건별 사유와 함께 C003으로 모아 반환한다.
        List<FieldError> unmet = collectUnmetConditions(section);
        if (!unmet.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT, unmet);
        }

        // 확정 본문 버전 = 현재 최신 초안 버전. AI 사전 검토 CURRENT가 초안 존재를 보장하지만 방어적으로 확인한다.
        int contentVersion = sectionDraftRepository
                .findTopByProjectSection_IdOrderByVersionDesc(sectionId)
                .map(SectionDraft::getVersion)
                .orElseThrow(() -> new BusinessException(ErrorCode.SECTION_DRAFT_NOT_FOUND));

        ProjectSection confirmed = sectionStatusService.markConfirmed(sectionId, userId, contentVersion);
        return SectionConfirmResponse.from(confirmed);
    }

    /**
     * 상태 게이트를 제외한 §6.3 확정 조건을 판정해 미충족 조건만 {@link FieldError} 목록으로 모은다.
     * 팀 검토 두 조건은 같은 목록에서 파생되므로 한 번의 조회로 함께 판정한다.
     */
    private List<FieldError> collectUnmetConditions(ProjectSection section) {
        Long projectId = section.getProject().getId();
        Long sectionId = section.getId();
        ConfirmReviewGate reviewGate = teamReviewService.evaluateConfirmReviewGate(projectId, sectionId);

        List<FieldError> unmet = new ArrayList<>();
        addIfUnmet(unmet, ConfirmReadinessCheck.AI_CHECK_CURRENT,
                section.getAiCheckStatus() == AiCheckStatus.CURRENT);
        addIfUnmet(unmet, ConfirmReadinessCheck.MEMBER_APPROVED,
                reviewGate.memberApprovalSatisfied());
        addIfUnmet(unmet, ConfirmReadinessCheck.NO_UNRESOLVED_REQUEST,
                reviewGate.noUnresolvedChangeRequest());
        addIfUnmet(unmet, ConfirmReadinessCheck.NO_ACTIVE_EDITOR,
                draftLeaseService.findActiveLease(sectionId).isEmpty());
        return unmet;
    }

    private void addIfUnmet(List<FieldError> unmet, ConfirmReadinessCheck check, boolean satisfied) {
        if (!satisfied) {
            unmet.add(new FieldError(check.name(), check.unsatisfiedReason()));
        }
    }
}
