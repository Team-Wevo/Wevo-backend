package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wevo.backend.ai.client.StructuredAiProviderRequest;
import com.wevo.backend.ai.client.StructuredOutputExecutionContext;
import com.wevo.backend.ai.context.AiOpinionContext;
import com.wevo.backend.ai.context.ContextChunk;
import com.wevo.backend.ai.context.ContextChunkPlan;
import com.wevo.backend.ai.context.ContextChunkPlanner;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.prompt.SynthesisPromptFactory;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.opinion.service.SubmittedOpinionView;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.user.domain.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SynthesisGeneratorTest {

    @Mock private ContextChunkPlanner chunkPlanner;
    @Mock private SynthesisPromptFactory promptFactory;
    @Mock private AiInvocationService invocationService;
    @Mock private AiJob job;

    private SynthesisGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new SynthesisGenerator(chunkPlanner, promptFactory, invocationService);
        given(job.getProject()).willReturn(mock(Project.class));
        given(job.getProjectSection()).willReturn(mock(ProjectSection.class));
        given(job.getRequestedBy()).willReturn(mock(User.class));
        given(job.getFeature()).willReturn(AiFeature.OPINION_SYNTHESIS);
        given(job.getPromptVersion()).willReturn(SynthesisContract.PROMPT_VERSION);
        given(job.getInputSnapshotHash()).willReturn("a".repeat(64));
    }

    @Test
    @DisplayName("여러 chunk를 모두 부분 합성한 뒤 한 번의 최종 병합 결과만 반환한다")
    void multipleChunks_areMergedOnlyAfterEveryPartialSucceeds() {
        SynthesisInputSnapshot snapshot = snapshot();
        ContextChunkPlan plan = twoChunkPlan();
        given(chunkPlanner.plan(eq(AiFeature.OPINION_SYNTHESIS), anyList(), any()))
                .willReturn(plan);

        @SuppressWarnings("unchecked")
        StructuredAiProviderRequest<SynthesisAiOutput> request =
                mock(StructuredAiProviderRequest.class);
        given(request.withExecutionContext(any())).willReturn(request);
        given(promptFactory.providerRequest(any(), any(), any())).willReturn(request);

        SynthesisAiOutput first = output("부분 1", List.of(1L));
        SynthesisAiOutput second = output("부분 2", List.of(2L));
        SynthesisAiOutput merged = output("최종", List.of(1L, 2L));
        given(invocationService.invokeStructured(any(), eq(request), any()))
                .willReturn(
                        new AiInvocationResult<>(first, 1L, UUID.randomUUID()),
                        new AiInvocationResult<>(second, 2L, UUID.randomUUID()),
                        new AiInvocationResult<>(merged, 3L, UUID.randomUUID())
                );

        SynthesisAiOutput result = generator.generate(job, snapshot);

        assertThat(result).isSameAs(merged);
        ArgumentCaptor<SynthesisPromptContext> contexts =
                ArgumentCaptor.forClass(SynthesisPromptContext.class);
        verify(promptFactory, org.mockito.Mockito.times(3))
                .providerRequest(contexts.capture(), any(), eq(SynthesisContract.PROMPT_VERSION));
        assertThat(contexts.getAllValues())
                .extracting(SynthesisPromptContext::mode)
                .containsExactly(
                        SynthesisPromptContext.PARTIAL,
                        SynthesisPromptContext.PARTIAL,
                        SynthesisPromptContext.FINAL_MERGE
                );
        assertThat(contexts.getAllValues().get(2).partials())
                .extracting(SynthesisPromptContext.PartialSynthesis::coveredOpinionIds)
                .containsExactly(List.of(1L), List.of(2L));
        ArgumentCaptor<StructuredOutputExecutionContext> executionContexts =
                ArgumentCaptor.forClass(StructuredOutputExecutionContext.class);
        verify(request, org.mockito.Mockito.times(3))
                .withExecutionContext(executionContexts.capture());
        assertThat(executionContexts.getAllValues())
                .containsExactly(
                        new StructuredOutputExecutionContext(SynthesisPromptContext.PARTIAL, 1),
                        new StructuredOutputExecutionContext(SynthesisPromptContext.PARTIAL, 2),
                        new StructuredOutputExecutionContext(SynthesisPromptContext.FINAL_MERGE, null)
                );
    }

    @Test
    @DisplayName("chunk 하나가 실패하면 최종 병합을 호출하지 않고 부분 결과를 반환하지 않는다")
    void partialFailure_skipsFinalMerge() {
        given(chunkPlanner.plan(eq(AiFeature.OPINION_SYNTHESIS), anyList(), any()))
                .willReturn(twoChunkPlan());
        @SuppressWarnings("unchecked")
        StructuredAiProviderRequest<SynthesisAiOutput> request =
                mock(StructuredAiProviderRequest.class);
        given(request.withExecutionContext(any())).willReturn(request);
        given(promptFactory.providerRequest(any(), any(), any())).willReturn(request);
        given(invocationService.invokeStructured(any(), eq(request), any()))
                .willReturn(new AiInvocationResult<>(
                        output("부분 1", List.of(1L)), 1L, UUID.randomUUID()))
                .willThrow(new IllegalStateException("두 번째 chunk 실패"));

        assertThatThrownBy(() -> generator.generate(job, snapshot()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("두 번째 chunk 실패");

        verify(promptFactory, org.mockito.Mockito.times(2)).providerRequest(any(), any(), any());
    }

    @Test
    @DisplayName("최종 병합이 실패하면 검증된 부분 결과도 성공 결과로 반환하지 않는다")
    void finalMergeFailure_doesNotReturnPartialResult() {
        given(chunkPlanner.plan(eq(AiFeature.OPINION_SYNTHESIS), anyList(), any()))
                .willReturn(twoChunkPlan());
        @SuppressWarnings("unchecked")
        StructuredAiProviderRequest<SynthesisAiOutput> request =
                mock(StructuredAiProviderRequest.class);
        given(request.withExecutionContext(any())).willReturn(request);
        given(promptFactory.providerRequest(any(), any(), any())).willReturn(request);
        given(invocationService.invokeStructured(any(), eq(request), any()))
                .willReturn(
                        new AiInvocationResult<>(
                                output("부분 1", List.of(1L)), 1L, UUID.randomUUID()),
                        new AiInvocationResult<>(
                                output("부분 2", List.of(2L)), 2L, UUID.randomUUID())
                )
                .willThrow(new IllegalStateException("최종 병합 실패"));

        assertThatThrownBy(() -> generator.generate(job, snapshot()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("최종 병합 실패");

        verify(promptFactory, org.mockito.Mockito.times(3)).providerRequest(any(), any(), any());
    }

    @Test
    @DisplayName("제출 의견이 없으면 chunk 계획과 Provider 호출 전에 O004로 실패한다")
    void noOpinion_failsBeforePlanningOrInvocation() {
        SynthesisInputSnapshot empty = new SynthesisInputSnapshot(List.of(), List.of(), 0);

        assertThatThrownBy(() -> generator.generate(job, empty))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.NO_SUBMITTED_OPINION));

        verify(chunkPlanner, never()).plan(any(), any(), any());
        verify(invocationService, never()).invokeStructured(any(), any(), any());
    }

    private SynthesisInputSnapshot snapshot() {
        return new SynthesisInputSnapshot(
                List.of(
                        new SubmittedOpinionView(
                                1L, 10L, "가", "첫 번째 의견입니다. 충분한 길이입니다.",
                                LocalDateTime.of(2026, 7, 28, 10, 0)),
                        new SubmittedOpinionView(
                                2L, 20L, "나", "두 번째 의견입니다. 충분한 길이입니다.",
                                LocalDateTime.of(2026, 7, 28, 10, 1))
                ),
                List.of(),
                1
        );
    }

    private ContextChunkPlan twoChunkPlan() {
        AiOpinionContext first =
                new AiOpinionContext(1L, "member-1", "첫 번째", "2026-07-28T10:00:00");
        AiOpinionContext second =
                new AiOpinionContext(2L, "member-2", "두 번째", "2026-07-28T10:01:00");
        return new ContextChunkPlan(
                List.of(
                        new ContextChunk(1, List.of(first), List.of(1L), 100),
                        new ContextChunk(2, List.of(second), List.of(2L), 100)
                ),
                List.of(1L, 2L),
                List.of(1L, 2L),
                true
        );
    }

    private SynthesisAiOutput output(String summary, List<Long> coverage) {
        return new SynthesisAiOutput(summary, List.of(coverage.getFirst()), coverage, List.of());
    }
}
