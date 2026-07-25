package com.wevo.backend.ai.service;

import com.wevo.backend.ai.domain.AiErrorType;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.domain.AiRequestStatus;
import com.wevo.backend.ai.dto.response.SynthesisResponse;
import com.wevo.backend.ai.dto.response.SynthesisResponse.AnswerResponse;
import com.wevo.backend.ai.dto.response.SynthesisResponse.CurrentSetResponse;
import com.wevo.backend.ai.dto.response.SynthesisResponse.DecisionResponse;
import com.wevo.backend.ai.dto.response.SynthesisResponse.FailureResponse;
import com.wevo.backend.ai.dto.response.SynthesisResponse.InheritedGapAnswerResponse;
import com.wevo.backend.ai.dto.response.SynthesisResponse.IssueResponse;
import com.wevo.backend.ai.dto.response.SynthesisResponse.LatestJobResponse;
import com.wevo.backend.ai.dto.response.SynthesisResponse.RelatedOpinionResponse;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.issue.service.SynthesisSetView;
import com.wevo.backend.issue.service.SynthesisSetViewService;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.section.domain.ProjectSection;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 섹션의 AI 의견 정리 결과(합의점·쟁점)를 조회한다. (API_SPEC §3.8.2)
 *
 * <p><b>최신 실행과 현재 세트를 따로 판정</b>하는 것이 이 조회의 핵심이다 — 재정리가 진행 중이거나
 * 실패해도 대체(supersede)는 성공 시에만 일어나므로(§3.8.1), 마지막으로 성공한 실행의 결과를
 * 그대로 현재 세트로 돌려준다. 이전 세트의 쟁점·결정·답변은 보존되지만 이 API로는 노출되지 않는다.
 *
 * <p><b>현재 세트 판정 기준</b>은 세트 자체의 생성 시각이 아니라 <b>최신 성공 AI 작업의
 * {@code resultId}</b>다 — 세트를 만든 주체가 AI 작업이므로, 작업 이력이 어떤 세트가 현재인지에
 * 대한 단일 근거가 된다.
 */
@Service
public class SynthesisQueryService {

    private static final AiFeature FEATURE = AiFeature.OPINION_SYNTHESIS;

    private final SectionAccessGuard sectionAccessGuard;
    private final AiJobRepository aiJobRepository;
    private final SynthesisSetViewService synthesisSetViewService;

    public SynthesisQueryService(SectionAccessGuard sectionAccessGuard,
                                 AiJobRepository aiJobRepository,
                                 SynthesisSetViewService synthesisSetViewService) {
        this.sectionAccessGuard = sectionAccessGuard;
        this.aiJobRepository = aiJobRepository;
        this.synthesisSetViewService = synthesisSetViewService;
    }

    /**
     * 정리 결과를 조회한다. 열람은 프로젝트 참여자 전원에게 열려 있다(정책서 §5.1.1 — 결정만 OWNER).
     *
     * <p><b>스냅샷 고정(REPEATABLE_READ)</b> — 이 응답은 작업 이력·정리 세트·쟁점·결정·답변을
     * 여러 쿼리로 읽어 하나로 조립한다. PostgreSQL 기본 격리(READ COMMITTED)는 <b>문장마다</b>
     * 새 스냅샷을 잡으므로, 조회 중간에 커밋이 끼면 실제로는 존재한 적 없는 조합이 나갈 수 있다 —
     * 예: 최신 작업을 진행 중으로 읽은 뒤 그 작업이 성공해, {@code latestJob=REQUESTED}인데
     * {@code currentSet}은 그 작업의 결과인 응답. 쟁점 쪽은 더 나빠서 {@code status=RESOLVED}인데
     * {@code decision}이 비어 있는 <b>자체 모순 응답</b>이 만들어진다({@code readOnly=true}로는
     * 막을 수 없다). 트랜잭션 전체를 한 스냅샷으로 고정해 이 조합을 제거한다.
     *
     * <p>읽기 전용 REPEATABLE READ 트랜잭션은 쓰기 충돌이 없어 직렬화 실패(40001)가 발생하지
     * 않으므로 재시도 처리가 필요하지 않다. 하위 조회 서비스는 이 트랜잭션에 참여해 같은 스냅샷을 쓴다.
     *
     * @throws com.wevo.backend.global.exception.BusinessException 섹션이 없거나 비멤버면
     *                                                            {@code SECTION_NOT_FOUND}(404, 존재 숨김)
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SynthesisResponse getSynthesis(Long projectSectionId, Long userId) {
        ProjectSection section = sectionAccessGuard.requireParticipantSection(projectSectionId, userId);

        Optional<AiJob> latestJob = aiJobRepository
                .findTopByProjectSection_IdAndFeatureOrderByQueuedAtDescIdDesc(projectSectionId, FEATURE);
        if (latestJob.isEmpty()) {
            // 실행 전은 오류가 아니라 빈 상태 — 화면 초기 진입에서 그대로 쓴다.
            return SynthesisResponse.notExecuted();
        }

        return SynthesisResponse.of(
                section.isSynthesisStale(),
                toLatestJobResponse(latestJob.get()),
                currentSet(projectSectionId));
    }

    /**
     * 최신 성공 실행의 결과를 현재 세트로 조립한다. 성공 이력이 없으면 {@code null}(응답에서 생략).
     */
    private CurrentSetResponse currentSet(Long projectSectionId) {
        return aiJobRepository
                .findTopByProjectSection_IdAndFeatureAndStatusOrderByCompletedAtDescIdDesc(
                        projectSectionId, FEATURE, AiJobStatus.SUCCEEDED)
                .map(succeeded -> linkedSetView(projectSectionId, succeeded))
                .map(this::toCurrentSetResponse)
                .orElse(null);
    }

    /**
     * 성공 작업의 {@code resultId}로 세트를 읽고, 그 세트가 <b>정말 이 작업의 결과인지</b> 확인한다.
     *
     * <p>DB가 보장하는 것은 {@code synthesis_sets.request_id → ai_jobs.request_id}뿐이다 —
     * {@code ai_jobs.result_id}는 기능마다 가리키는 대상이 달라 FK를 걸 수 없고, "작업의 resultId가
     * 그 작업이 만든 세트를 가리킨다"는 불변식은 저장 코드만 지킨다. 검증이 없으면 같은 섹션의
     * <b>다른 세대 세트</b>를 가리키는 어긋난 참조도 정상 응답으로 나가므로, 여기서 요청 ID 일치를
     * 확인해 조용한 오답 대신 즉시 실패로 드러낸다.
     */
    private SynthesisSetView linkedSetView(Long projectSectionId, AiJob succeeded) {
        SynthesisSetView view =
                synthesisSetViewService.getSetView(projectSectionId, succeeded.getResultId());
        if (!succeeded.getRequestId().equals(view.setId())) {
            throw new IllegalStateException(
                    "성공한 AI 작업과 정리 세트의 requestId가 일치하지 않습니다: resultId="
                            + succeeded.getResultId());
        }
        return view;
    }

    private LatestJobResponse toLatestJobResponse(AiJob job) {
        AiRequestStatus status = AiRequestStatus.from(job.getStatus());
        return new LatestJobResponse(
                job.getRequestId(),
                status,
                status == AiRequestStatus.FAILED ? toFailureResponse(job) : null);
    }

    /**
     * 실패 사유를 외부 코드와 안전한 메시지로 옮긴다.
     *
     * <p>제공자 원문이 아니라 작업에 저장된 정제 메시지({@code safeErrorMessage})만 노출한다
     * (CLAUDE.md §7). 취소처럼 오류 유형이 남지 않는 종료는 일반 코드로 접는다.
     */
    private FailureResponse toFailureResponse(AiJob job) {
        AiErrorType errorType = job.getFinalErrorType();
        ErrorCode errorCode = errorType == null
                ? ErrorCode.AI_PROVIDER_ERROR
                : errorType.toErrorCode();
        String message = job.getSafeErrorMessage() == null || job.getSafeErrorMessage().isBlank()
                ? errorCode.getMessage()
                : job.getSafeErrorMessage();
        return new FailureResponse(errorCode.getCode(), message);
    }

    private CurrentSetResponse toCurrentSetResponse(SynthesisSetView view) {
        return new CurrentSetResponse(
                view.setId(),
                view.consensusSummary(),
                view.issues().stream().map(this::toIssueResponse).toList(),
                view.inheritedGapAnswers().stream()
                        .map(inherited -> new InheritedGapAnswerResponse(
                                inherited.issueId(), inherited.answerId()))
                        .toList());
    }

    private IssueResponse toIssueResponse(SynthesisSetView.IssueView issue) {
        return new IssueResponse(
                issue.issueId(),
                issue.type(),
                issue.status(),
                issue.description(),
                issue.relatedOpinions().stream()
                        .map(related -> new RelatedOpinionResponse(
                                related.opinionId(), related.authorName(), related.excerpt()))
                        .toList(),
                issue.question(),
                issue.options(),
                toDecisionResponse(issue.decision()),
                issue.evidenceRequested(),
                toAnswerResponse(issue.answer()));
    }

    private DecisionResponse toDecisionResponse(SynthesisSetView.DecisionView decision) {
        if (decision == null) {
            return null;
        }
        return new DecisionResponse(
                decision.selectedOption(), decision.customInput(), decision.decidedAt());
    }

    private AnswerResponse toAnswerResponse(SynthesisSetView.AnswerView answer) {
        if (answer == null) {
            return null;
        }
        return new AnswerResponse(
                answer.answerId(), answer.authorName(), answer.content(), answer.answeredAt());
    }
}
