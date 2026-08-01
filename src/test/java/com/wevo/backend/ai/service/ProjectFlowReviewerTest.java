package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.context.AiInputSnapshotHasher;
import com.wevo.backend.ai.context.AiProjectBrief;
import com.wevo.backend.ai.context.AiProjectIdentity;
import com.wevo.backend.ai.context.AiTemplateContext;
import com.wevo.backend.ai.context.AiTokenBudgetEstimator;
import com.wevo.backend.ai.context.ProjectFlowReviewContext;
import com.wevo.backend.ai.context.ProjectFlowReviewSectionContext;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.ai.dto.model.ProjectFlowReviewOutput;
import com.wevo.backend.ai.prompt.ProjectFlowReviewPromptFactory;
import com.wevo.backend.ai.prompt.PromptRegistry;
import com.wevo.backend.ai.prompt.PromptRenderer;
import com.wevo.backend.ai.prompt.PromptResourceLoader;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ProjectFlowReviewerTest {
    @Test
    @SuppressWarnings("unchecked")
    void nineMaximumLengthSectionsUseAllThirtySixPairsWithoutTruncation() {
        AiProperties properties = new AiProperties("none", new AiProperties.ModelOptions(
                "test-model", Duration.ofSeconds(1), 96_000, 4_096, 120_000, 8_000,
                AiProperties.ModelOptions.CONSERVATIVE_CHAR_V1,
                AiProperties.ModelOptions.REJECT_OVERSIZED_INPUT_V1,
                0, Duration.ZERO, Duration.ZERO), Map.of(), null);
        var validator = new ProjectFlowReviewOutputValidator();
        var definition = new ProjectFlowReviewOutputDefinition(validator);
        var factory = new ProjectFlowReviewPromptFactory(
                new PromptRegistry(new PromptResourceLoader()), new PromptRenderer(),
                new AiInputSnapshotHasher(), definition);
        AiInvocationService invocation = mock(AiInvocationService.class);
        when(invocation.invokeStructured(any(), any(), any())).thenReturn(
                new AiInvocationResult<>(new ProjectFlowReviewOutput(List.of()), 1L, UUID.randomUUID()));
        var reviewer = new ProjectFlowReviewer(new AiTokenBudgetEstimator(properties), factory,
                invocation, validator);
        AiJob job = mock(AiJob.class);
        Project project = mock(Project.class);
        User user = mock(User.class);
        when(job.getProject()).thenReturn(project);
        when(job.getRequestedBy()).thenReturn(user);
        when(job.getFeature()).thenReturn(AiFeature.PROJECT_FLOW_REVIEW);
        when(job.getPromptVersion()).thenReturn(ProjectFlowReviewContract.PROMPT_VERSION);
        when(job.getInputSnapshotHash()).thenReturn("a".repeat(64));
        when(project.getId()).thenReturn(1L);
        when(user.getId()).thenReturn(7L);

        ProjectFlowReviewOutput result = reviewer.review(job, context(9, 10_000));

        assertThat(result.findings()).isEmpty();
        verify(invocation, times(36)).invokeStructured(any(), any(), any());
    }

    @Test
    void rejectsSectionCountOutsideProductContractBeforeProviderInvocation() {
        AiProperties properties = new AiProperties("none", new AiProperties.ModelOptions(
                "test-model", Duration.ofSeconds(1), 96_000, 4_096, 120_000, 8_000,
                AiProperties.ModelOptions.CONSERVATIVE_CHAR_V1,
                AiProperties.ModelOptions.REJECT_OVERSIZED_INPUT_V1,
                0, Duration.ZERO, Duration.ZERO), Map.of(), null);
        var validator = new ProjectFlowReviewOutputValidator();
        var factory = new ProjectFlowReviewPromptFactory(
                new PromptRegistry(new PromptResourceLoader()), new PromptRenderer(),
                new AiInputSnapshotHasher(), new ProjectFlowReviewOutputDefinition(validator));
        AiInvocationService invocation = mock(AiInvocationService.class);
        var reviewer = new ProjectFlowReviewer(new AiTokenBudgetEstimator(properties), factory,
                invocation, validator);
        AiJob job = mock(AiJob.class);

        assertThatThrownBy(() -> reviewer.review(job, context(1, 10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("전체 흐름 점검 section 수는 2개 이상 9개 이하여야 합니다.");
        assertThatThrownBy(() -> reviewer.review(job, context(10, 10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("전체 흐름 점검 section 수는 2개 이상 9개 이하여야 합니다.");
        verifyNoInteractions(invocation);
    }

    private ProjectFlowReviewContext context(int count, int contentLength) {
        List<ProjectFlowReviewSectionContext> sections = IntStream.rangeClosed(1, count)
                .mapToObj(index -> new ProjectFlowReviewSectionContext((long) index, "key-" + index,
                        index, 1, "섹션 " + index, new AiTemplateContext("key-" + index, null, null),
                        "가".repeat(contentLength))).toList();
        return new ProjectFlowReviewContext(
                new AiProjectIdentity(1L, "프로젝트", com.wevo.backend.project.domain.OutputType.PRESENTATION),
                new AiProjectBrief(null, null, "고객"), count, count, sections, List.of());
    }
}
