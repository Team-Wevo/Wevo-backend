package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.wevo.backend.ai.domain.AiErrorType;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.domain.AiRequestStatus;
import com.wevo.backend.ai.dto.response.SynthesisResponse;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.issue.domain.IssueStatus;
import com.wevo.backend.issue.domain.IssueType;
import com.wevo.backend.issue.service.CurrentSynthesisSetReference;
import com.wevo.backend.issue.service.CurrentSynthesisSetResolver;
import com.wevo.backend.issue.service.SynthesisSetView;
import com.wevo.backend.issue.service.SynthesisSetView.AnswerView;
import com.wevo.backend.issue.service.SynthesisSetView.DecisionView;
import com.wevo.backend.issue.service.SynthesisSetView.InheritedGapAnswerView;
import com.wevo.backend.issue.service.SynthesisSetView.IssueView;
import com.wevo.backend.issue.service.SynthesisSetView.RelatedOpinionView;
import com.wevo.backend.issue.service.SynthesisSetViewService;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.section.domain.ProjectSection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SynthesisQueryServiceTest {

    private static final long SECTION_ID = 10L;
    private static final long USER_ID = 7L;
    private static final long RESULT_ID = 55L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 25, 15, 0);

    @Mock private SectionAccessGuard sectionAccessGuard;
    @Mock private AiJobRepository aiJobRepository;
    @Mock private CurrentSynthesisSetResolver currentSynthesisSetResolver;
    @Mock private SynthesisSetViewService synthesisSetViewService;
    @Mock private ProjectSection section;

    private SynthesisQueryService service;

    @BeforeEach
    void setUp() {
        service = new SynthesisQueryService(
                sectionAccessGuard,
                aiJobRepository,
                currentSynthesisSetResolver,
                synthesisSetViewService);
        given(sectionAccessGuard.requireParticipantSection(SECTION_ID, USER_ID)).willReturn(section);
        given(section.isSynthesisStale()).willReturn(false);
    }

    @Test
    @DisplayName("정리 실행 이력이 없으면 exists=false만 반환하고 세트를 조회하지 않는다")
    void noExecution_returnsNotExecuted() {
        givenLatestJob(null);

        SynthesisResponse response = service.getSynthesis(SECTION_ID, USER_ID);

        assertThat(response.exists()).isFalse();
        assertThat(response.synthesisStale()).isNull();
        assertThat(response.latestJob()).isNull();
        assertThat(response.currentSet()).isNull();
        verifyNoInteractions(currentSynthesisSetResolver, synthesisSetViewService);
    }

    @Test
    @DisplayName("진행 중 실행은 REQUESTED로 접히고 성공 이력이 없으면 currentSet을 생략한다")
    void runningWithoutSuccess_returnsRequestedWithoutSet() {
        UUID requestId = UUID.randomUUID();
        givenLatestJob(job(requestId, AiJobStatus.RUNNING, null, null, null));
        givenLatestSucceededJob(null);

        SynthesisResponse response = service.getSynthesis(SECTION_ID, USER_ID);

        assertThat(response.exists()).isTrue();
        assertThat(response.latestJob().requestId()).isEqualTo(requestId);
        assertThat(response.latestJob().status()).isEqualTo(AiRequestStatus.REQUESTED);
        assertThat(response.latestJob().failure()).isNull();
        assertThat(response.currentSet()).isNull();
    }

    @Test
    @DisplayName("재정리가 실패해도 이전 성공 세트를 그대로 반환한다 (대체는 성공 시에만)")
    void failedRetry_keepsPreviousSet() {
        UUID failedRequestId = UUID.randomUUID();
        UUID succeededRequestId = UUID.randomUUID();
        givenLatestJob(job(failedRequestId, AiJobStatus.FAILED,
                AiErrorType.PROVIDER_TIMEOUT, "AI 응답 시간이 초과되었습니다.", null));
        givenLatestSucceededJob(job(succeededRequestId, AiJobStatus.SUCCEEDED, null, null, RESULT_ID));
        given(section.isSynthesisStale()).willReturn(true);
        given(synthesisSetViewService.getSetView(SECTION_ID, RESULT_ID))
                .willReturn(fullSetView(succeededRequestId));

        SynthesisResponse response = service.getSynthesis(SECTION_ID, USER_ID);

        assertThat(response.synthesisStale()).isTrue();
        assertThat(response.latestJob().status()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(response.latestJob().failure().errorCode()).isEqualTo("AI005");
        assertThat(response.latestJob().failure().message()).isEqualTo("AI 응답 시간이 초과되었습니다.");
        assertThat(response.currentSet()).isNotNull();
        assertThat(response.currentSet().setId()).isEqualTo(succeededRequestId);
        assertThat(response.currentSet().consensusSummary()).isEqualTo("협업 도구 부족이 공통 문제다.");
    }

    @Test
    @DisplayName("입력 변경으로 폐기된 실행(STALE)은 FAILED + AI024로 노출한다")
    void staleJob_mapsToAi024() {
        givenLatestJob(job(UUID.randomUUID(), AiJobStatus.STALE,
                AiErrorType.STALE_INPUT, "AI 작업 입력이 최신 상태와 일치하지 않습니다.", null));
        givenLatestSucceededJob(null);

        SynthesisResponse response = service.getSynthesis(SECTION_ID, USER_ID);

        assertThat(response.latestJob().status()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(response.latestJob().failure().errorCode()).isEqualTo("AI024");
    }

    @Test
    @DisplayName("오류 유형이 남지 않은 종료(취소)는 일반 코드 AI999와 기본 메시지로 접는다")
    void cancelledJob_fallsBackToGenericFailure() {
        givenLatestJob(job(UUID.randomUUID(), AiJobStatus.CANCELLED, null, null, null));
        givenLatestSucceededJob(null);

        SynthesisResponse response = service.getSynthesis(SECTION_ID, USER_ID);

        assertThat(response.latestJob().status()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(response.latestJob().failure().errorCode()).isEqualTo("AI999");
        assertThat(response.latestJob().failure().message())
                .isEqualTo(ErrorCode.AI_PROVIDER_ERROR.getMessage());
    }

    @Test
    @DisplayName("CONFLICT는 질문·선택지·결정을, GAP은 근거 요청 여부·답변을 채운다")
    void issuesAreMappedByType() {
        UUID succeededRequestId = UUID.randomUUID();
        givenLatestJob(job(succeededRequestId, AiJobStatus.SUCCEEDED, null, null, RESULT_ID));
        givenLatestSucceededJob(job(succeededRequestId, AiJobStatus.SUCCEEDED, null, null, RESULT_ID));
        given(synthesisSetViewService.getSetView(SECTION_ID, RESULT_ID))
                .willReturn(fullSetView(succeededRequestId));

        SynthesisResponse response = service.getSynthesis(SECTION_ID, USER_ID);

        SynthesisResponse.IssueResponse conflict = response.currentSet().issues().get(0);
        assertThat(conflict.type()).isEqualTo(IssueType.CONFLICT);
        assertThat(conflict.status()).isEqualTo(IssueStatus.RESOLVED);
        assertThat(conflict.question()).isEqualTo("어느 사용자층을 우선할까요?");
        assertThat(conflict.options()).containsExactly("대학생", "직장인");
        assertThat(conflict.decision().selectedOption()).isEqualTo("대학생");
        assertThat(conflict.decision().customInput()).isNull();
        assertThat(conflict.decision().decidedAt()).isEqualTo(NOW);
        assertThat(conflict.evidenceRequested()).isNull();
        assertThat(conflict.answer()).isNull();
        assertThat(conflict.relatedOpinions())
                .extracting(SynthesisResponse.RelatedOpinionResponse::authorName)
                .containsExactly("팀원A");

        SynthesisResponse.IssueResponse gap = response.currentSet().issues().get(1);
        assertThat(gap.type()).isEqualTo(IssueType.GAP);
        assertThat(gap.question()).isNull();
        assertThat(gap.options()).isNull();
        assertThat(gap.decision()).isNull();
        assertThat(gap.evidenceRequested()).isTrue();
        assertThat(gap.answer().answerId()).isEqualTo(901L);
        assertThat(gap.answer().content()).isEqualTo("작년 설문 결과를 근거로 붙입니다.");

        assertThat(response.currentSet().inheritedGapAnswers())
                .extracting(SynthesisResponse.InheritedGapAnswerResponse::answerId)
                .containsExactly(700L);
    }

    @Test
    @DisplayName("성공 작업의 resultId가 다른 세트를 가리키면 조용히 반환하지 않고 실패한다")
    void resultIdPointingToAnotherSet_fails() {
        UUID succeededRequestId = UUID.randomUUID();
        givenLatestJob(job(succeededRequestId, AiJobStatus.SUCCEEDED, null, null, RESULT_ID));
        givenLatestSucceededJob(job(succeededRequestId, AiJobStatus.SUCCEEDED, null, null, RESULT_ID));
        // 같은 섹션의 다른 세대 세트 — 섹션 소속 검증만으로는 걸러지지 않는다.
        given(synthesisSetViewService.getSetView(SECTION_ID, RESULT_ID))
                .willReturn(fullSetView(UUID.randomUUID()));

        assertThatThrownBy(() -> service.getSynthesis(SECTION_ID, USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requestId가 일치하지 않습니다");
    }

    @Test
    @DisplayName("조회 트랜잭션은 REPEATABLE_READ로 고정한다 (여러 쿼리의 스냅샷 일관성)")
    void getSynthesis_pinsRepeatableRead() throws Exception {
        Transactional transactional = SynthesisQueryService.class
                .getMethod("getSynthesis", Long.class, Long.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
        assertThat(transactional.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }

    @Test
    @DisplayName("비멤버·없는 섹션이면 S001을 그대로 전파하고 작업을 조회하지 않는다")
    void nonMember_propagatesSectionNotFound() {
        given(sectionAccessGuard.requireParticipantSection(SECTION_ID, USER_ID))
                .willThrow(new BusinessException(ErrorCode.SECTION_NOT_FOUND));

        assertThatThrownBy(() -> service.getSynthesis(SECTION_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SECTION_NOT_FOUND));

        verifyNoInteractions(
                aiJobRepository, currentSynthesisSetResolver, synthesisSetViewService);
    }

    private void givenLatestJob(AiJob job) {
        given(aiJobRepository.findTopByProjectSection_IdAndFeatureOrderByQueuedAtDescIdDesc(
                SECTION_ID, AiFeature.OPINION_SYNTHESIS))
                .willReturn(Optional.ofNullable(job));
    }

    private void givenLatestSucceededJob(AiJob job) {
        CurrentSynthesisSetReference reference = job == null
                ? null
                : new CurrentSynthesisSetReference(job.getRequestId(), job.getResultId());
        given(currentSynthesisSetResolver.findCurrent(SECTION_ID))
                .willReturn(Optional.ofNullable(reference));
    }

    private AiJob job(UUID requestId, AiJobStatus status, AiErrorType errorType,
                      String safeErrorMessage, Long resultId) {
        AiJob job = org.mockito.Mockito.mock(AiJob.class);
        given(job.getRequestId()).willReturn(requestId);
        given(job.getStatus()).willReturn(status);
        given(job.getFinalErrorType()).willReturn(errorType);
        given(job.getSafeErrorMessage()).willReturn(safeErrorMessage);
        given(job.getResultId()).willReturn(resultId);
        return job;
    }

    /** CONFLICT(결정 완료) + GAP(요청·답변 완료) + 승계 답변 참조를 모두 가진 세트. */
    private SynthesisSetView fullSetView(UUID setId) {
        IssueView conflict = new IssueView(
                101L,
                IssueType.CONFLICT,
                IssueStatus.RESOLVED,
                "우선 사용자층이 갈립니다.",
                List.of(new RelatedOpinionView(11L, "팀원A", "대학생이 주 사용자입니다.")),
                "어느 사용자층을 우선할까요?",
                List.of("대학생", "직장인"),
                new DecisionView("대학생", null, NOW),
                null,
                null);
        IssueView gap = new IssueView(
                102L,
                IssueType.GAP,
                IssueStatus.RESOLVED,
                "시장 규모 근거가 없습니다.",
                List.of(),
                null,
                null,
                null,
                true,
                new AnswerView(901L, "팀원B", "작년 설문 결과를 근거로 붙입니다.", NOW));
        return new SynthesisSetView(
                setId,
                "협업 도구 부족이 공통 문제다.",
                List.of(conflict, gap),
                List.of(new InheritedGapAnswerView(50L, 700L)));
    }
}
