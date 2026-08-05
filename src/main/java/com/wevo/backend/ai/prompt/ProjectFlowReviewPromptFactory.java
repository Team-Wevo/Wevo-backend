package com.wevo.backend.ai.prompt;

import com.wevo.backend.ai.client.StructuredAiProviderRequest;
import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.client.StructuredPromptFormatter;
import com.wevo.backend.ai.context.AiInputSnapshotHasher;
import com.wevo.backend.ai.context.AiTokenBudgetInput;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.dto.model.ProjectFlowReviewOutput;
import com.wevo.backend.ai.service.ProjectFlowReviewOutputDefinition;
import com.wevo.backend.ai.service.ProjectFlowReviewPromptContext;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ProjectFlowReviewPromptFactory {
    public static final PromptTemplateId PROMPT_ID = new PromptTemplateId("project-flow-review", 1);

    private final PromptRegistry registry;
    private final PromptRenderer renderer;
    private final AiInputSnapshotHasher hasher;
    private final ProjectFlowReviewOutputDefinition outputDefinition;

    public ProjectFlowReviewPromptFactory(PromptRegistry registry, PromptRenderer renderer,
                                          AiInputSnapshotHasher hasher,
                                          ProjectFlowReviewOutputDefinition outputDefinition) {
        this.registry = registry;
        this.renderer = renderer;
        this.hasher = hasher;
        this.outputDefinition = outputDefinition;
    }

    public StructuredAiProviderRequest<ProjectFlowReviewOutput> providerRequest(
            ProjectFlowReviewPromptContext context) {
        return providerRequest(context, PROMPT_ID.trackingValue());
    }

    public StructuredAiProviderRequest<ProjectFlowReviewOutput> providerRequest(
            ProjectFlowReviewPromptContext context, String promptVersion) {
        RenderedPrompt prompt = renderer.render(registry.get(PROMPT_ID, promptVersion),
                Map.of("reviewContext", serialize(context)));
        Map<Long, String> contents = context.sections().stream().collect(Collectors.toUnmodifiableMap(
                section -> section.sectionId(), section -> section.content()));
        return new StructuredAiProviderRequest<>(AiFeature.PROJECT_FLOW_REVIEW, prompt,
                outputDefinition.get(), StructuredOutputValidationContext.forResourceContents(contents));
    }

    public AiTokenBudgetInput tokenBudgetInput(ProjectFlowReviewPromptContext context) {
        return tokenBudgetInput(context, PROMPT_ID.trackingValue());
    }

    public AiTokenBudgetInput tokenBudgetInput(
            ProjectFlowReviewPromptContext context, String promptVersion) {
        var request = providerRequest(context, promptVersion);
        return AiTokenBudgetInput.of(request.prompt().systemPrompt(),
                StructuredPromptFormatter.initialUserPrompt(request));
    }

    private String serialize(ProjectFlowReviewPromptContext context) {
        return new String(hasher.canonicalSnapshot(context).canonicalBytes(), StandardCharsets.UTF_8);
    }
}
