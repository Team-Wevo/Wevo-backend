package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredAiProviderRequest;
import com.wevo.backend.ai.context.AuthorIntentContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.dto.model.AuthorIntentExtractionOutput;
import com.wevo.backend.ai.prompt.AuthorIntentPromptFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnBean(AiInvocationService.class)
public class AuthorIntentExtractor {

    private final AuthorIntentPromptFactory promptFactory;
    private final AiInvocationService invocationService;

    public AuthorIntentExtractor(
            AuthorIntentPromptFactory promptFactory,
            AiInvocationService invocationService
    ) {
        this.promptFactory = promptFactory;
        this.invocationService = invocationService;
    }

    public AuthorIntentExtractionOutput extract(AiJob job, AuthorIntentContext context) {
        StructuredAiProviderRequest<AuthorIntentExtractionOutput> request =
                promptFactory.providerRequest(context, job.getPromptVersion());
        AiUsageStartCommand usage = new AiUsageStartCommand(
                job,
                job.getProject(),
                job.getProjectSection(),
                job.getRequestedBy(),
                AiFeature.AUTHOR_INTENT_EXTRACTION,
                job.getPromptVersion(),
                job.getInputSnapshotHash());
        return invocationService.invokeStructured(
                usage,
                request,
                response -> new AiProcessedResult<>(response.result(), null)).value();
    }
}
