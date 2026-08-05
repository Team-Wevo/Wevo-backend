package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredAiProviderRequest;
import com.wevo.backend.ai.context.AiOpinionContext;
import com.wevo.backend.ai.context.AiTokenBudgetEstimator;
import com.wevo.backend.ai.context.ContextChunkPlan;
import com.wevo.backend.ai.context.ContextChunkPlanner;
import com.wevo.backend.ai.context.OpinionClusteringContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.dto.model.OpinionClusteringOutput;
import com.wevo.backend.ai.prompt.OpinionClusteringPromptFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

/** 의견 단위 chunk를 분류하고 필요하면 부분 결과를 최종 partition으로 병합한다. */
@Service
@ConditionalOnBean(AiInvocationService.class)
public class OpinionClusterer {

    private final ContextChunkPlanner chunkPlanner;
    private final AiTokenBudgetEstimator budgetEstimator;
    private final OpinionClusteringPromptFactory promptFactory;
    private final AiInvocationService invocationService;

    public OpinionClusterer(
            ContextChunkPlanner chunkPlanner,
            AiTokenBudgetEstimator budgetEstimator,
            OpinionClusteringPromptFactory promptFactory,
            AiInvocationService invocationService
    ) {
        this.chunkPlanner = chunkPlanner;
        this.budgetEstimator = budgetEstimator;
        this.promptFactory = promptFactory;
        this.invocationService = invocationService;
    }

    public OpinionClusteringOutput cluster(AiJob job, OpinionClusteringContext context) {
        if (job == null || context == null) {
            throw new IllegalArgumentException("AI 작업과 clustering context는 필수입니다.");
        }
        if (context.opinions().size() < OpinionClusteringContract.MIN_OPINION_COUNT) {
            throw new IllegalArgumentException("clustering 최소 의견 수를 충족하지 않습니다.");
        }

        ContextChunkPlan plan = chunkPlanner.plan(
                AiFeature.OPINION_CLUSTERING,
                context.opinions(),
                opinions -> promptFactory.tokenBudgetInput(
                        partialContext(context, opinions), ids(opinions), job.getPromptVersion())
        );
        AiUsageStartCommand usage = new AiUsageStartCommand(
                job,
                job.getProject(),
                job.getProjectSection(),
                job.getRequestedBy(),
                AiFeature.OPINION_CLUSTERING,
                job.getPromptVersion(),
                job.getInputSnapshotHash()
        );

        List<OpinionClusteringPromptContext.PartialClustering> partials = new ArrayList<>();
        for (var chunk : plan.chunks()) {
            OpinionClusteringOutput output = invoke(
                    usage,
                    promptFactory.providerRequest(
                            partialContext(context, chunk.opinions()),
                            Set.copyOf(chunk.opinionIds()),
                            job.getPromptVersion()
                    )
            );
            partials.add(new OpinionClusteringPromptContext.PartialClustering(
                    chunk.index(), chunk.opinionIds(), output));
        }
        if (partials.size() == 1) {
            return partials.getFirst().output();
        }

        OpinionClusteringPromptContext merge = new OpinionClusteringPromptContext(
                OpinionClusteringPromptContext.FINAL_MERGE,
                context.projectId(),
                context.sectionId(),
                context.sectionTitle(),
                context.template(),
                List.of(),
                partials
        );
        Set<Long> allIds = Set.copyOf(plan.eligibleOpinionIds());
        budgetEstimator.requireWithinBudget(
                AiFeature.OPINION_CLUSTERING,
                promptFactory.tokenBudgetInput(merge, allIds, job.getPromptVersion()));
        return invoke(usage, promptFactory.providerRequest(merge, allIds, job.getPromptVersion()));
    }

    private OpinionClusteringOutput invoke(
            AiUsageStartCommand usage,
            StructuredAiProviderRequest<OpinionClusteringOutput> request
    ) {
        return invocationService.invokeStructured(
                usage,
                request,
                response -> new AiProcessedResult<>(response.result(), null)
        ).value();
    }

    private OpinionClusteringPromptContext partialContext(
            OpinionClusteringContext context,
            List<AiOpinionContext> opinions
    ) {
        return new OpinionClusteringPromptContext(
                OpinionClusteringPromptContext.PARTIAL,
                context.projectId(),
                context.sectionId(),
                context.sectionTitle(),
                context.template(),
                opinions,
                List.of()
        );
    }

    private Set<Long> ids(List<AiOpinionContext> opinions) {
        return opinions.stream().map(AiOpinionContext::opinionId)
                .collect(java.util.stream.Collectors.toSet());
    }
}
