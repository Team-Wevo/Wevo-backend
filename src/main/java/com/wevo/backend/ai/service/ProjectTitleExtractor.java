package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredAiProviderRequest;
import com.wevo.backend.ai.context.ProjectTitleContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.dto.model.ProjectTitleSuggestionOutput;
import com.wevo.backend.ai.prompt.ProjectTitlePromptFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnBean(AiInvocationService.class)
public class ProjectTitleExtractor {

    private final ProjectTitlePromptFactory promptFactory;
    private final AiInvocationService invocationService;

    public ProjectTitleExtractor(
            ProjectTitlePromptFactory promptFactory,
            AiInvocationService invocationService
    ) {
        this.promptFactory = promptFactory;
        this.invocationService = invocationService;
    }

    public ProjectTitleSuggestionOutput generate(AiJob job, ProjectTitleContext context) {
        StructuredAiProviderRequest<ProjectTitleSuggestionOutput> request =
                promptFactory.providerRequest(context, job.getPromptVersion());
        AiUsageStartCommand usage = new AiUsageStartCommand(
                job,
                job.getProject(),
                job.getProjectSection(),
                job.getRequestedBy(),
                AiFeature.PROJECT_TITLE_SUGGESTION,
                job.getPromptVersion(),
                job.getInputSnapshotHash());
        return invocationService.invokeStructured(
                usage,
                request,
                response -> new AiProcessedResult<>(response.result(), null)).value();
    }
}
