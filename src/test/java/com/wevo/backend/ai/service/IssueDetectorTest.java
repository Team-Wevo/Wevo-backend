package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredAiProviderRequest;
import com.wevo.backend.ai.client.StructuredOutputExecutionContext;
import com.wevo.backend.ai.context.AiInputSnapshot;
import com.wevo.backend.ai.context.AiInputSnapshotHasher;
import com.wevo.backend.ai.context.AiOpinionContext;
import com.wevo.backend.ai.context.AiProjectBrief;
import com.wevo.backend.ai.context.AiProjectIdentity;
import com.wevo.backend.ai.context.AiSectionContext;
import com.wevo.backend.ai.context.AiTemplateContext;
import com.wevo.backend.ai.context.AssembledAiContext;
import com.wevo.backend.ai.context.ContextChunk;
import com.wevo.backend.ai.context.ContextChunkPlan;
import com.wevo.backend.ai.context.ContextChunkPlanner;
import com.wevo.backend.ai.context.IssueDetectionContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.dto.model.IssueDetectionIssueOutput;
import com.wevo.backend.ai.dto.model.IssueDetectionOutput;
import com.wevo.backend.ai.exception.AiProviderException;
import com.wevo.backend.ai.prompt.IssueDetectionPromptFactory;
import com.wevo.backend.ai.prompt.PromptRegistry;
import com.wevo.backend.ai.prompt.PromptRenderer;
import com.wevo.backend.ai.prompt.PromptResourceLoader;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.issue.domain.IssueType;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.user.domain.User;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IssueDetectorTest {

    @Test
    void invokesEveryChunkThenAiFinalMergeForCrossChunkIssue() {
        TestFixture fixture = fixture(List.of(
                new IssueDetectionOutput(List.of(conflict("방향 A 후보", 1L))),
                new IssueDetectionOutput(List.of(conflict("방향 B 후보", 2L))),
                new IssueDetectionOutput(List.of(new IssueDetectionIssueOutput(
                        IssueType.CONFLICT,
                        "chunk 간 충돌",
                        List.of(1L, 2L),
                        "어느 방향을 선택할까요?",
                        List.of("A", "B"))))
        ), -1);

        IssueDetectionResult result = fixture.detector().detect(fixture.assembled(), fixture.usage());

        assertThat(result.issues()).singleElement()
                .satisfies(issue -> assertThat(issue.evidenceOpinionIds()).containsExactly(1L, 2L));
        assertThat(fixture.invocationService().requests).hasSize(3);
        assertThat(fixture.invocationService().requests.getFirst().validationContext().allowedResourceIds())
                .containsExactly(1L);
        assertThat(fixture.invocationService().requests.get(1).validationContext().allowedResourceIds())
                .containsExactly(2L);
        assertThat(fixture.invocationService().requests.get(2).validationContext().allowedResourceIds())
                .containsExactlyInAnyOrder(1L, 2L);
        assertThat(fixture.invocationService().requests.getFirst().executionContext())
                .isEqualTo(new StructuredOutputExecutionContext("PARTIAL", 1));
        assertThat(fixture.invocationService().requests.get(2).executionContext())
                .isEqualTo(new StructuredOutputExecutionContext("FINAL_MERGE", null));
        assertThat(fixture.invocationService().requests.get(2).prompt().userPrompt())
                .contains("FINAL_MERGE", "coveredOpinionIds", "chunkIndex");
    }

    @Test
    void propagatesAnyChunkFailureWithoutReturningPartialResult() {
        TestFixture fixture = fixture(List.of(
                new IssueDetectionOutput(List.of(conflict("충돌", 1L))),
                new IssueDetectionOutput(List.of(conflict("충돌", 2L))),
                new IssueDetectionOutput(List.of())
        ), 2);

        assertThatThrownBy(() -> fixture.detector().detect(fixture.assembled(), fixture.usage()))
                .isInstanceOf(AiProviderException.class)
                .extracting(exception -> ((AiProviderException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_PROVIDER_TIMEOUT);
        assertThat(fixture.invocationService().requests).hasSize(2);
    }

    @Test
    void propagatesFinalMergeFailureWithoutReturningConcatenatedPartials() {
        TestFixture fixture = fixture(List.of(
                new IssueDetectionOutput(List.of(conflict("충돌 A", 1L))),
                new IssueDetectionOutput(List.of(conflict("충돌 B", 2L))),
                new IssueDetectionOutput(List.of())
        ), 3);

        assertThatThrownBy(() -> fixture.detector().detect(fixture.assembled(), fixture.usage()))
                .isInstanceOf(AiProviderException.class)
                .extracting(exception -> ((AiProviderException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_PROVIDER_TIMEOUT);
        assertThat(fixture.invocationService().requests).hasSize(3);
    }

    @Test
    void finalMergeAppliesGlobalLimitInsteadOfValidatingConcatenatedChunkCandidates() {
        IssueDetectionOutput finalOutput = new IssueDetectionOutput(List.of(
                conflict("최종 충돌", 1L),
                gap("최종 공백", 2L)));
        TestFixture fixture = fixture(List.of(
                new IssueDetectionOutput(List.of(
                        conflict("후보 충돌 1", 1L),
                        conflict("후보 충돌 2", 1L),
                        gap("후보 공백 1", 1L))),
                new IssueDetectionOutput(List.of(
                        conflict("후보 충돌 3", 2L),
                        conflict("후보 충돌 4", 2L),
                        gap("후보 공백 2", 2L))),
                finalOutput
        ), -1);

        IssueDetectionResult result = fixture.detector().detect(fixture.assembled(), fixture.usage());

        assertThat(result.issues()).isEqualTo(finalOutput.issues());
        assertThat(fixture.invocationService().requests).hasSize(3);
    }

    @Test
    void singleChunkUsesDirectAnalysisWithoutFinalMerge() {
        IssueDetectionOutput direct = new IssueDetectionOutput(List.of(conflict("직접 충돌", 1L)));
        TestFixture fixture = fixture(List.of(direct), -1, 1);

        IssueDetectionResult result = fixture.detector().detect(fixture.assembled(), fixture.usage());

        assertThat(result.issues()).isEqualTo(direct.issues());
        assertThat(fixture.invocationService().requests).singleElement()
                .satisfies(request -> assertThat(request.executionContext())
                        .isEqualTo(new StructuredOutputExecutionContext("DIRECT", null)));
    }

    @Test
    void rejectsAuditMetadataThatDoesNotMatchSnapshotOrPrompt() {
        TestFixture fixture = fixture(List.of(
                new IssueDetectionOutput(List.of()),
                new IssueDetectionOutput(List.of())
        ), -1);
        AiUsageStartCommand wrong = new AiUsageStartCommand(
                mock(Project.class),
                mock(ProjectSection.class),
                mock(User.class),
                AiFeature.ISSUE_DETECTION,
                "wrong-prompt:v1",
                fixture.assembled().snapshot().inputSnapshotHash()
        );

        assertThatThrownBy(() -> fixture.detector().detect(fixture.assembled(), wrong))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(fixture.invocationService().requests).isEmpty();
    }

    private TestFixture fixture(List<IssueDetectionOutput> outputs, int failureCall) {
        return fixture(outputs, failureCall, 2);
    }

    private TestFixture fixture(
            List<IssueDetectionOutput> outputs,
            int failureCall,
            int chunkCount
    ) {
        IssueDetectionContext context = context();
        ContextChunkPlan plan = plan(context.opinions(), chunkCount);
        ContextChunkPlanner planner = mock(ContextChunkPlanner.class);
        when(planner.plan(eq(AiFeature.ISSUE_DETECTION), anyList(), any())).thenReturn(plan);

        IssueDetectionOutputValidator validator = new IssueDetectionOutputValidator();
        IssueDetectionPromptFactory promptFactory = new IssueDetectionPromptFactory(
                new PromptRegistry(new PromptResourceLoader()),
                new PromptRenderer(),
                new AiInputSnapshotHasher(),
                new IssueDetectionOutputDefinition(validator)
        );
        StubInvocationService invocationService = new StubInvocationService(outputs, failureCall);
        IssueDetector detector = new IssueDetector(
                planner,
                promptFactory,
                invocationService,
                new IssueDetectionResultMerger(validator)
        );

        AiInputSnapshot snapshot = new AiInputSnapshot(
                "{}".getBytes(StandardCharsets.UTF_8),
                "a".repeat(64)
        );
        AssembledAiContext<IssueDetectionContext> assembled =
                new AssembledAiContext<>(context, snapshot);
        AiUsageStartCommand usage = new AiUsageStartCommand(
                mock(Project.class),
                mock(ProjectSection.class),
                mock(User.class),
                AiFeature.ISSUE_DETECTION,
                IssueDetectionPromptFactory.PROMPT_ID.trackingValue(),
                snapshot.inputSnapshotHash()
        );
        return new TestFixture(detector, invocationService, assembled, usage);
    }

    private ContextChunkPlan plan(List<AiOpinionContext> opinions, int chunkCount) {
        if (chunkCount == 1) {
            return new ContextChunkPlan(
                    List.of(new ContextChunk(
                            1, opinions, List.of(1L, 2L), 100)),
                    List.of(1L, 2L),
                    List.of(1L, 2L),
                    true);
        }
        return new ContextChunkPlan(
                List.of(
                        new ContextChunk(1, List.of(opinions.getFirst()), List.of(1L), 100),
                        new ContextChunk(2, List.of(opinions.get(1)), List.of(2L), 100)
                ),
                List.of(1L, 2L),
                List.of(1L, 2L),
                true
        );
    }

    private IssueDetectionContext context() {
        return new IssueDetectionContext(
                new AiProjectIdentity(10L, "프로젝트", OutputType.PROPOSAL),
                new AiProjectBrief("설명", "아이디어", "사용자"),
                new AiSectionContext(
                        20L,
                        "문제",
                        1,
                        ProjectSectionStatus.SYNTHESIZING,
                        2,
                        false,
                        new AiTemplateContext("problem", "설명", "가이드")
                ),
                List.of(
                        new AiOpinionContext(1L, "member-1", "의견 1", "2026-07-25T10:00:00"),
                        new AiOpinionContext(2L, "member-2", "의견 2", "2026-07-25T10:01:00")
                )
        );
    }

    private IssueDetectionIssueOutput conflict(String description, Long evidenceId) {
        return new IssueDetectionIssueOutput(
                IssueType.CONFLICT,
                description,
                List.of(evidenceId),
                "질문",
                List.of("A", "B")
        );
    }

    private IssueDetectionIssueOutput gap(String description, Long evidenceId) {
        return new IssueDetectionIssueOutput(
                IssueType.GAP,
                description,
                List.of(evidenceId),
                null,
                List.of()
        );
    }

    private record TestFixture(
            IssueDetector detector,
            StubInvocationService invocationService,
            AssembledAiContext<IssueDetectionContext> assembled,
            AiUsageStartCommand usage
    ) {
    }

    private static final class StubInvocationService extends AiInvocationService {

        private final List<IssueDetectionOutput> outputs;
        private final int failureCall;
        private final List<StructuredAiProviderRequest<?>> requests = new ArrayList<>();

        private StubInvocationService(List<IssueDetectionOutput> outputs, int failureCall) {
            super(null, null);
            this.outputs = outputs;
            this.failureCall = failureCall;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <I, O> AiInvocationResult<O> invokeStructured(
                AiUsageStartCommand startCommand,
                StructuredAiProviderRequest<I> request,
                StructuredAiResultHandler<I, O> resultHandler
        ) {
            requests.add(request);
            int call = requests.size();
            if (call == failureCall) {
                throw new AiProviderException(
                        ErrorCode.AI_PROVIDER_TIMEOUT,
                        new IllegalStateException("timeout")
                );
            }
            return new AiInvocationResult<>(
                    (O) outputs.get(call - 1),
                    (long) call,
                    UUID.randomUUID()
            );
        }
    }
}
