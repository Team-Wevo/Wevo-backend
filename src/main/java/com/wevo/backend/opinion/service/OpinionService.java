package com.wevo.backend.opinion.service;

import com.wevo.backend.ai.service.OpinionContentGuardrailService;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.response.FieldError;
import com.wevo.backend.opinion.domain.Opinion;
import com.wevo.backend.opinion.domain.OpinionStatus;
import com.wevo.backend.opinion.dto.request.OpinionDraftRequest;
import com.wevo.backend.opinion.dto.response.MyOpinionResponse;
import com.wevo.backend.opinion.dto.response.OpinionCollectionStatusResponse;
import com.wevo.backend.opinion.dto.response.OpinionDraftResponse;
import com.wevo.backend.opinion.dto.response.OpinionGateCloseResponse;
import com.wevo.backend.opinion.dto.response.OpinionGateReopenResponse;
import com.wevo.backend.opinion.dto.response.OpinionSubmitResponse;
import com.wevo.backend.opinion.dto.response.SubmittedOpinionListResponse;
import com.wevo.backend.opinion.repository.OpinionRepository;
import com.wevo.backend.project.service.ProjectMemberRosterQueryService;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.project.service.VerifiedParticipantSection;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.service.DraftLeaseService;
import com.wevo.backend.section.service.SectionStatusService;
import com.wevo.backend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 의견(my-opinion) 관련 로직. (제품 정책서 §4·§5)
 *
 * <ul>
 *   <li>한 멤버는 한 섹션에 <b>하나의 의견</b>만 가진다 — 임시저장(upsert)이 생성을 겸한다.</li>
 *   <li>의견 저장은 섹션이 의견 수집 단계({@code COLLECTING})일 때만 가능하다.
 *       수집 마감(gate CLOSED) 시 섹션은 {@code SYNTHESIZING} 으로 전이되므로,
 *       별도 게이트 컬럼 없이 섹션 상태로 판정한다.</li>
 *   <li><b>재제출 모델(§4.1)</b> — 임시저장은 작업본만 갱신하고 제출본은 유지된다.
 *       재제출하면 제출본이 최신 작업본으로 갱신된다.</li>
 * </ul>
 *
 * <p>의견 API는 OWNER와 MEMBER 모두 사용할 수 있으므로 프로젝트 참여 여부만 확인하며,
 * 공통 인가 규칙(비멤버 404 존재 숨김 포함)은 {@link SectionAccessGuard}에 위임한다.
 */
@Service
@Transactional(readOnly = true)
public class OpinionService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Set<ProjectSectionStatus> REOPENABLE_STATUSES = Set.of(
            ProjectSectionStatus.SYNTHESIZING,
            ProjectSectionStatus.DRAFTING,
            ProjectSectionStatus.REVIEWING
    );

    private final SectionAccessGuard sectionAccessGuard;
    private final OpinionRepository opinionRepository;
    private final DraftLeaseService draftLeaseService;
    private final SectionStatusService sectionStatusService;
    private final UserRepository userRepository;
    private final ProjectMemberRosterQueryService memberRosterQueryService;
    private final OpinionContentGuardrailService contentGuardrailService;
    private final TransactionTemplate transactionTemplate;

    public OpinionService(SectionAccessGuard sectionAccessGuard,
                          OpinionRepository opinionRepository,
                          DraftLeaseService draftLeaseService,
                          SectionStatusService sectionStatusService,
                          UserRepository userRepository,
                          ProjectMemberRosterQueryService memberRosterQueryService,
                          OpinionContentGuardrailService contentGuardrailService,
                          TransactionTemplate transactionTemplate) {
        this.sectionAccessGuard = sectionAccessGuard;
        this.opinionRepository = opinionRepository;
        this.draftLeaseService = draftLeaseService;
        this.sectionStatusService = sectionStatusService;
        this.userRepository = userRepository;
        this.memberRosterQueryService = memberRosterQueryService;
        this.contentGuardrailService = contentGuardrailService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 내 의견을 임시저장한다. 의견이 없으면 DRAFT 로 새로 만들고, 있으면 본문을 덮어쓴다.
     *
     * <p>섹션 행을 배타 잠금으로 조회해 조회→INSERT 사이의 경합을 직렬화한다.
     * 같은 사용자의 자동저장 요청이 겹쳐도 유니크 제약 충돌(409) 없이 뒤 요청이 덮어쓴다.
     *
     * <p>잠금 범위를 섹션 단위로 둔 것은 의도된 선택이다 — 프로젝트 인원이 1~4명(정책)이라
     * 직렬화 비용이 무시 가능하고, 이후 수집 마감(gate close)과 저장이 겹치는 경합도 같은 잠금으로
     * 막는다. 더 좁은 잠금(의견 행)은 최초 저장 시 잠글 행이 없어 INSERT 경합을 막지 못한다.
     */
    @Transactional
    public OpinionDraftResponse saveDraft(Long projectSectionId, Long userId, OpinionDraftRequest request) {
        ProjectSection section =
                sectionAccessGuard.requireParticipantSectionForUpdate(projectSectionId, userId);
        if (section.getStatus() != ProjectSectionStatus.COLLECTING) {
            throw new BusinessException(ErrorCode.OPINION_COLLECTION_CLOSED);
        }

        Opinion opinion = opinionRepository.findByProjectSection_IdAndAuthor_Id(projectSectionId, userId)
                .map(existing -> {
                    existing.updateContent(request.content());
                    // 이미 영속 상태이므로 save 없이 flush 만으로 auditing(updatedAt)을 응답에 반영한다.
                    opinionRepository.flush();
                    return existing;
                })
                .orElseGet(() -> opinionRepository.save(Opinion.builder()
                        .projectSection(section)
                        .author(userRepository.getReferenceById(userId))
                        .content(request.content())
                        .status(OpinionStatus.DRAFT)
                        .build()));

        return OpinionDraftResponse.from(opinion);
    }

    /**
     * 내 의견(작업본 기준)을 조회한다. 아직 작성하지 않았으면 예외가 아니라 {@code exists=false} 응답을 반환한다.
     */
    public MyOpinionResponse getMyOpinion(Long projectSectionId, Long userId) {
        sectionAccessGuard.requireParticipantSection(projectSectionId, userId);
        return opinionRepository.findByProjectSection_IdAndAuthor_Id(projectSectionId, userId)
                .map(MyOpinionResponse::from)
                .orElseGet(MyOpinionResponse::empty);
    }

    /**
     * 섹션에 제출된 팀원 의견 목록을 조회한다. (정책서 §5 공개 게이트)
     *
     * <p>요청자가 이 섹션에 제출한 적이 없으면 목록을 숨기고 제출 건수만 반환한다(베끼기 방지).
     * 게이트 판정은 "내 SUBMITTED 의견 존재"로 파생한다 — 의견 삭제가 정책상 미지원이라
     * 한 번 제출한 사실은 사라지지 않는다.
     *
     * <p>섹션 상태와 무관하게 조회할 수 있다 — 수집 마감 후에도 정리(SYNTHESIZING) 단계에서
     * 팀이 근거 의견을 봐야 한다. DRAFT 의견은 쿼리 단계에서 제외되어 어떤 경우에도 노출되지 않는다.
     */
    public SubmittedOpinionListResponse getSubmittedOpinions(Long projectSectionId, Long userId) {
        sectionAccessGuard.requireParticipantSection(projectSectionId, userId);
        List<Opinion> submitted = opinionRepository
                .findAllWithAuthorByProjectSectionIdAndStatus(projectSectionId, OpinionStatus.SUBMITTED);

        boolean everSubmitted = submitted.stream()
                .anyMatch(opinion -> opinion.getAuthor().getId().equals(userId));
        if (!everSubmitted) {
            return SubmittedOpinionListResponse.hidden(submitted.size());
        }
        return SubmittedOpinionListResponse.visible(submitted);
    }

    /**
     * 섹션의 의견 수집 현황을 조회한다. (제출 N/M + 멤버별 진행 상태 — 정책서 §4.5)
     *
     * <p><b>참여자 전체가 호출할 수 있지만 응답 범위는 역할로 갈린다</b> — 팀원은 집계(N/M)만,
     * 팀장은 멤버별 상태({@code items})까지 받는다. 정책서 §4.3은 제출 전 사용자에게 "모인 의견
     * N개 카운트"까지만 허용하고, §4.5의 미제출 경고는 <b>팀장</b>에게 주도록 규정한다. 응답에 의견
     * 본문이 없더라도 "누가 제출했고 누가 재편집 중인지"를 팀원에게 열 근거는 두 절 어디에도 없다.
     *
     * <p>특히 {@code hasUnsubmittedChanges}(재편집 중 여부)는 다른 어떤 API 로도 얻을 수 없어
     * 이 응답이 유일한 노출 경로다.
     *
     * <p>분모는 <b>OWNER 를 포함한 멤버 전원</b>이다 — 팀장도 의견을 작성·제출하는 참여자다.
     * (팀 검토 분모가 팀장을 빼는 것과 다르며, 이는 의도된 차이다.)
     *
     * <p>수집이 마감된 뒤에도 조회할 수 있다 — 마감 시점의 참여율은 정리(SYNTHESIZING) 이후에도
     * "이 결과가 몇 명의 의견에서 나왔는지"를 설명하는 근거이므로 섹션 상태로 막지 않고,
     * 게이트 개방 여부는 {@code collectionOpen} 으로 함께 내려 화면이 분기하게 한다.
     */
    public OpinionCollectionStatusResponse getCollectionStatus(Long projectSectionId, Long userId) {
        VerifiedParticipantSection granted =
                sectionAccessGuard.requireParticipantSectionAccess(projectSectionId, userId);
        ProjectSection section = granted.section();

        Map<Long, Opinion> opinionByAuthorId = opinionRepository
                .findAllWithAuthorByProjectSectionId(projectSectionId).stream()
                .collect(Collectors.toMap(opinion -> opinion.getAuthor().getId(), Function.identity()));

        return OpinionCollectionStatusResponse.of(
                section.getStatus() == ProjectSectionStatus.COLLECTING,
                memberRosterQueryService.getParticipants(section.getProject().getId()),
                opinionByAuthorId,
                granted.isOwner());
    }

    /**
     * 내 작업본을 팀에 제출한다. 재편집 후 재제출하면 제출본이 최신 작업본으로 갱신된다. (§4.1)
     *
     * <p>섹션 단위 배타 잠금으로 임시저장·제출·수집 마감 경합을 직렬화한다.
     * <b>작업본 변경 없이 다시 호출하면</b> 수집 마감 이후라도 최초 제출 시각을 유지한 채
     * 멱등 성공으로 응답한다. 작업본이 바뀐 재제출은 게이트가 열려 있어야 한다(§4.4).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public OpinionSubmitResponse submitMyOpinion(Long projectSectionId, Long userId) {
        // ① 짧은 읽기로 멱등·게이트·길이 검사 후 가드레일 검사 대상 본문을 확정한다.
        SubmitPreparation prepared =
                transactionTemplate.execute(status -> prepareSubmit(projectSectionId, userId));
        if (prepared.completed() != null) {
            return prepared.completed();
        }
        // ② 섹션 잠금·트랜잭션 밖에서 AI 가드레일 판정 — 외부 provider 호출이 섹션 잠금을 쥐고
        //    다른 임시저장·제출·마감을 대기시키지 않게 한다. (판정 실패 시 fail-closed: O006)
        contentGuardrailService.requireAcceptable(prepared.section(), userId, prepared.content());
        // ③ 짧은 쓰기로 섹션을 다시 잠그고 게이트·본문 불변 재확인 후 제출을 반영한다.
        return transactionTemplate.execute(
                status -> finalizeSubmit(projectSectionId, userId, prepared.content()));
    }

    /**
     * 제출 ① 단계 — 짧은 읽기. 멱등·게이트·길이를 검사하고 가드레일이 판정할 본문을 확정한다.
     * 이미 제출됐고 작업본 변경이 없으면 AI 호출 없이 응답을 반환한다({@code completed}).
     *
     * <p>섹션 행을 배타 잠금으로 잡아 본문 스냅샷을 임시저장·마감과 직렬화하되, 이 트랜잭션은
     * <b>짧게 끝나 잠금이 ② 가드레일(외부 호출) 전에 풀린다</b> — 잠금이 AI 호출을 가로지르지 않는다.
     * 반환한 섹션은 트랜잭션 종료 후 detached 되므로, ② 단계 가드레일이 읽을 연관(project·template)을
     * 이 트랜잭션 안에서 초기화해 둔다 — 잠금 밖 지연로딩을 피한다.
     */
    private SubmitPreparation prepareSubmit(Long projectSectionId, Long userId) {
        ProjectSection section =
                sectionAccessGuard.requireParticipantSectionForUpdate(projectSectionId, userId);
        Optional<Opinion> opinionOptional = opinionRepository
                .findByProjectSection_IdAndAuthor_Id(projectSectionId, userId);

        if (opinionOptional.isPresent()
                && opinionOptional.get().getStatus() == OpinionStatus.SUBMITTED
                && !opinionOptional.get().hasUnsubmittedChanges()) {
            return SubmitPreparation.completed(OpinionSubmitResponse.from(opinionOptional.get()));
        }
        if (section.getStatus() != ProjectSectionStatus.COLLECTING) {
            throw new BusinessException(ErrorCode.OPINION_COLLECTION_CLOSED);
        }
        Opinion opinion = opinionOptional.orElseThrow(() ->
                new BusinessException(ErrorCode.OPINION_NOT_FOUND,
                        List.of(new FieldError("sectionId", "no draft opinion to submit"))));
        validateContentForSubmit(opinion.getContent());
        // 잠금 밖(가드레일)에서 읽을 연관을 미리 초기화한다.
        section.getProject().getId();
        if (section.getTemplate() != null) {
            section.getTemplate().getGuideText();
        }
        return SubmitPreparation.pending(section, opinion.getContent());
    }

    /**
     * 제출 ③ 단계 — 짧은 쓰기. 섹션을 다시 잠그고 게이트·본문 불변을 재확인한 뒤 제출을 반영한다.
     * ① 이후 본문이 바뀌었으면(작성자가 그 사이 임시저장을 커밋) 가드레일 판정이 낡았으므로
     * {@code CONFLICT}(C003)로 되돌려 재제출을 유도한다.
     */
    private OpinionSubmitResponse finalizeSubmit(
            Long projectSectionId, Long userId, String checkedContent) {
        ProjectSection section =
                sectionAccessGuard.requireParticipantSectionForUpdate(projectSectionId, userId);
        Opinion opinion = opinionRepository
                .findByProjectSection_IdAndAuthor_Id(projectSectionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OPINION_NOT_FOUND,
                        List.of(new FieldError("sectionId", "no draft opinion to submit"))));

        if (opinion.getStatus() == OpinionStatus.SUBMITTED && !opinion.hasUnsubmittedChanges()) {
            return OpinionSubmitResponse.from(opinion);
        }
        if (section.getStatus() != ProjectSectionStatus.COLLECTING) {
            throw new BusinessException(ErrorCode.OPINION_COLLECTION_CLOSED);
        }
        // 가드레일이 검사한 본문과 지금 제출할 본문이 달라졌으면 판정이 낡았다 — 재제출을 유도한다.
        if (!checkedContent.equals(opinion.getContent())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    List.of(new FieldError("content", "본문이 수정되었습니다. 다시 제출해주세요.")));
        }
        LocalDateTime submittedAt = LocalDateTime.now(KST);
        opinion.submit(submittedAt);
        // 의견 수집 단계에서 유일하게 사람이 남기는 흔적이라, 이걸 빼면 수집 기간 내내
        // 목록 카드의 "마지막 작업" 지점이 프로젝트 생성 시각에 멈춘다. (API_SPEC §3.2.2)
        section.recordActivity(submittedAt);
        return OpinionSubmitResponse.from(opinion);
    }

    /** 제출 준비 결과 — {@code completed}가 있으면 AI 없이 즉시 응답, 없으면 가드레일·확정으로 진행. */
    private record SubmitPreparation(
            OpinionSubmitResponse completed, ProjectSection section, String content) {
        static SubmitPreparation completed(OpinionSubmitResponse response) {
            return new SubmitPreparation(response, null, null);
        }

        static SubmitPreparation pending(ProjectSection section, String content) {
            return new SubmitPreparation(null, section, content);
        }
    }

    /**
     * 의견 수집을 마감한다. 마감은 OWNER만 실행할 수 있고 제출 의견이 하나 이상 있어야 한다.
     * 섹션 잠금을 먼저 획득해 늦게 도착한 임시저장·제출 요청과 상태 전이가 섞이지 않게 한다.
     */
    @Transactional
    public OpinionGateCloseResponse closeOpinionGate(Long projectSectionId, Long userId) {
        ProjectSection section = sectionAccessGuard.requireOwnedSectionForUpdate(projectSectionId, userId);
        if (section.getStatus() != ProjectSectionStatus.COLLECTING) {
            throw new BusinessException(ErrorCode.INVALID_SECTION_STATUS_TRANSITION);
        }
        if (!opinionRepository.existsByProjectSection_IdAndStatus(projectSectionId, OpinionStatus.SUBMITTED)) {
            throw new BusinessException(ErrorCode.NO_SUBMITTED_OPINION);
        }

        sectionStatusService.markSynthesizing(projectSectionId, userId);
        return OpinionGateCloseResponse.from(section, LocalDateTime.now(KST));
    }

    /**
     * 미확정 섹션의 의견 수집을 다시 연다. 확정 섹션은 재오픈할 수 없고, 기존 초안이 있으면
     * 재정리 필요 플래그가 설정된다.
     */
    @Transactional
    public OpinionGateReopenResponse reopenOpinionGate(Long projectSectionId, Long userId) {
        ProjectSection section =
                sectionAccessGuard.requireOwnedSectionForUpdate(projectSectionId, userId);
        if (!REOPENABLE_STATUSES.contains(section.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_SECTION_STATUS_TRANSITION);
        }
        draftLeaseService.releaseOwnLeaseOrRejectOther(projectSectionId, userId);

        ProjectSection reopened = sectionStatusService.markCollecting(projectSectionId, userId);
        return OpinionGateReopenResponse.from(reopened, LocalDateTime.now(KST));
    }

    private void validateContentForSubmit(String content) {
        if (content == null || content.isBlank()
                || content.length() < Opinion.MIN_CONTENT_LENGTH
                || content.length() > Opinion.MAX_CONTENT_LENGTH) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    List.of(new FieldError(
                            "content",
                            "content must be between " + Opinion.MIN_CONTENT_LENGTH
                                    + " and " + Opinion.MAX_CONTENT_LENGTH + " characters"
                    ))
            );
        }
    }
}
