package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredAiProviderRequest;
import com.wevo.backend.ai.client.StructuredOutputExecutionContext;
import com.wevo.backend.ai.context.AiOpinionContext;
import com.wevo.backend.ai.context.AssembledAiContext;
import com.wevo.backend.ai.context.ContextChunkPlan;
import com.wevo.backend.ai.context.ContextChunkPlanner;
import com.wevo.backend.ai.context.IssueDetectionContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.dto.model.IssueDetectionOutput;
import com.wevo.backend.ai.prompt.IssueDetectionPromptFactory;
import com.wevo.backend.ai.service.IssueDetectionPromptContext.PartialIssueDetection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

/**
 * 쟁점 감지 Provider 호출과 chunk 병합 경계.
 *
 * <p>영속 set 생성과 AiJob 성공 전이는 AI-07/AI-10 오케스트레이터가 이 결과를 받은 뒤
 * 하나의 result writer 트랜잭션에서 수행한다.</p>
 */
@Service
@ConditionalOnBean(AiInvocationService.class)
public class IssueDetector {

    private final ContextChunkPlanner chunkPlanner;
    private final IssueDetectionPromptFactory promptFactory;
    private final AiInvocationService invocationService;
    private final IssueDetectionResultMerger resultMerger;

    public IssueDetector(
            ContextChunkPlanner chunkPlanner,
            IssueDetectionPromptFactory promptFactory,
            AiInvocationService invocationService,
            IssueDetectionResultMerger resultMerger
    ) {
        this.chunkPlanner = chunkPlanner;
        this.promptFactory = promptFactory;
        this.invocationService = invocationService;
        this.resultMerger = resultMerger;
    }

    public IssueDetectionResult detect(
            AssembledAiContext<IssueDetectionContext> assembled,
            AiUsageStartCommand usageStartCommand
    ) {
        requireMatchingInvocation(assembled, usageStartCommand);
        IssueDetectionContext fullContext = assembled.context();
        ContextChunkPlan plan = chunkPlanner.plan(
                AiFeature.ISSUE_DETECTION,
                fullContext.opinions(),
                opinions -> {
                    IssueDetectionPromptContext context = promptContext(
                            IssueDetectionPromptContext.PARTIAL, fullContext, opinions, List.of());
                    return promptFactory.tokenBudgetInput(
                            context,
                            opinionIds(opinions),
                            usageStartCommand.promptVersion());
                }
        );

        if (plan.chunks().size() == 1) {
            var chunk = plan.chunks().getFirst();
            IssueDetectionOutput output = invoke(
                    usageStartCommand,
                    promptFactory.providerRequest(
                            promptContext(
                                    IssueDetectionPromptContext.DIRECT,
                                    fullContext,
                                    chunk.opinions(),
                                    List.of()),
                            opinionIds(chunk.opinions()),
                            usageStartCommand.promptVersion()),
                    IssueDetectionPromptContext.DIRECT,
                    null);
            return resultMerger.merge(plan, output);
        }

        List<PartialIssueDetection> partials = new ArrayList<>();
        for (var chunk : plan.chunks()) {
            IssueDetectionOutput output = invoke(
                    usageStartCommand,
                    promptFactory.providerRequest(
                            promptContext(
                                    IssueDetectionPromptContext.PARTIAL,
                                    fullContext,
                                    chunk.opinions(),
                                    List.of()),
                            opinionIds(chunk.opinions()),
                            usageStartCommand.promptVersion()),
                    IssueDetectionPromptContext.PARTIAL,
                    chunk.index());
            partials.add(new PartialIssueDetection(
                    chunk.index(), chunk.opinionIds(), output));
        }

        IssueDetectionOutput output = invoke(
                usageStartCommand,
                promptFactory.providerRequest(
                        promptContext(
                                IssueDetectionPromptContext.FINAL_MERGE,
                                fullContext,
                                List.of(),
                                partials),
                        Set.copyOf(plan.eligibleOpinionIds()),
                        usageStartCommand.promptVersion()),
                IssueDetectionPromptContext.FINAL_MERGE,
                null);
        return resultMerger.merge(plan, output);
    }

    private IssueDetectionOutput invoke(
            AiUsageStartCommand usageStartCommand,
            StructuredAiProviderRequest<IssueDetectionOutput> request,
            String stage,
            Integer chunkIndex
    ) {
        return invocationService.invokeStructured(
                usageStartCommand,
                request.withExecutionContext(
                        new StructuredOutputExecutionContext(stage, chunkIndex)),
                response -> new AiProcessedResult<>(response.result(), null)
        ).value();
    }

    private void requireMatchingInvocation(
            AssembledAiContext<IssueDetectionContext> assembled,
            AiUsageStartCommand usageStartCommand
    ) {
        if (assembled == null || usageStartCommand == null) {
            throw new IllegalArgumentException("조립된 context와 AI 감사 시작 정보는 필수입니다.");
        }
        if (usageStartCommand.feature() != AiFeature.ISSUE_DETECTION
                || !com.wevo.backend.ai.prompt.PromptTemplateId
                        .parseTrackingValue(usageStartCommand.promptVersion()).promptName()
                        .equals(IssueDetectionPromptFactory.PROMPT_ID.promptName())
                || !usageStartCommand.inputSnapshotHash().equals(
                        assembled.snapshot().inputSnapshotHash())
                || assembled.context().section().status() != ProjectSectionStatus.SYNTHESIZING) {
            throw new IllegalArgumentException("쟁점 감지 context와 AI 감사 시작 정보가 일치해야 합니다.");
        }
        if (usageStartCommand.aiJob() != null
                && (!usageStartCommand.aiJob().getSourceVersion().equals(
                        assembled.context().sourceVersion())
                || !usageStartCommand.aiJob().getSchemaVersion().equals(
                        IssueDetectionOutputDefinition.SCHEMA_ID.trackingValue()))) {
            throw new IllegalArgumentException("쟁점 감지 AiJob의 source/schema version이 일치해야 합니다.");
        }
    }

    private IssueDetectionPromptContext promptContext(
            String mode,
            IssueDetectionContext context,
            List<AiOpinionContext> opinions,
            List<PartialIssueDetection> partials
    ) {
        return new IssueDetectionPromptContext(
                mode,
                context.project(),
                context.projectBrief(),
                context.section(),
                opinions,
                partials);
    }

    private Set<Long> opinionIds(List<AiOpinionContext> opinions) {
        return opinions.stream()
                .map(AiOpinionContext::opinionId)
                .collect(Collectors.toSet());
    }
}
