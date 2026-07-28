package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredAiProviderRequest;
import com.wevo.backend.ai.context.AiOpinionContext;
import com.wevo.backend.ai.context.AssembledAiContext;
import com.wevo.backend.ai.context.ContextChunkPlan;
import com.wevo.backend.ai.context.ContextChunkPlanner;
import com.wevo.backend.ai.context.IssueDetectionContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.dto.model.IssueDetectionOutput;
import com.wevo.backend.ai.prompt.IssueDetectionPromptFactory;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import java.util.ArrayList;
import java.util.List;
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
                opinions -> promptFactory.tokenBudgetInput(withOpinions(fullContext, opinions))
        );

        List<IssueDetectionOutput> outputs = new ArrayList<>();
        for (var chunk : plan.chunks()) {
            StructuredAiProviderRequest<IssueDetectionOutput> request =
                    promptFactory.providerRequest(withOpinions(fullContext, chunk.opinions()));
            AiInvocationResult<IssueDetectionOutput> invocation = invocationService.invokeStructured(
                    usageStartCommand,
                    request,
                    response -> new AiProcessedResult<>(response.result(), null)
            );
            outputs.add(invocation.value());
        }
        return resultMerger.merge(plan, outputs);
    }

    private void requireMatchingInvocation(
            AssembledAiContext<IssueDetectionContext> assembled,
            AiUsageStartCommand usageStartCommand
    ) {
        if (assembled == null || usageStartCommand == null) {
            throw new IllegalArgumentException("조립된 context와 AI 감사 시작 정보는 필수입니다.");
        }
        if (usageStartCommand.feature() != AiFeature.ISSUE_DETECTION
                || !usageStartCommand.promptVersion().equals(
                        IssueDetectionPromptFactory.PROMPT_ID.trackingValue())
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

    private IssueDetectionContext withOpinions(
            IssueDetectionContext context,
            List<AiOpinionContext> opinions
    ) {
        return new IssueDetectionContext(
                context.project(),
                context.projectBrief(),
                context.section(),
                opinions
        );
    }
}
