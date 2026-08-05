package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredAiProviderRequest;
import com.wevo.backend.ai.context.ReviewIntentComparisonContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.dto.model.ReviewIntentComparisonOutput;
import com.wevo.backend.ai.prompt.ReviewIntentComparisonPromptFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnBean(AiInvocationService.class)
public class ReviewIntentComparator {

    private final ReviewIntentComparisonPromptFactory promptFactory;
    private final AiInvocationService invocationService;

    public ReviewIntentComparator(
            ReviewIntentComparisonPromptFactory promptFactory,
            AiInvocationService invocationService
    ) {
        this.promptFactory = promptFactory;
        this.invocationService = invocationService;
    }

    public ReviewIntentComparisonOutput compare(
            AiJob job,
            ReviewIntentComparisonContext context
    ) {
        StructuredAiProviderRequest<ReviewIntentComparisonOutput> request =
                promptFactory.providerRequest(context, job.getPromptVersion());
        AiUsageStartCommand usage = new AiUsageStartCommand(
                job,
                job.getProject(),
                job.getProjectSection(),
                job.getRequestedBy(),
                AiFeature.REVIEW_INTENT_COMPARISON,
                job.getPromptVersion(),
                job.getInputSnapshotHash());
        return invocationService.invokeStructured(
                usage,
                request,
                response -> new AiProcessedResult<>(response.result(), null)).value();
    }
}
