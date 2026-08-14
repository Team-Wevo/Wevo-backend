package com.wevo.backend.ai.prompt;

import com.wevo.backend.ai.client.StructuredAiProviderRequest;
import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.context.AiInputSnapshotHasher;
import com.wevo.backend.ai.context.ProjectTitleContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.dto.model.ProjectTitleSuggestionOutput;
import com.wevo.backend.ai.service.ProjectTitleOutputDefinition;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ProjectTitlePromptFactory {

    public static final PromptTemplateId PROMPT_ID = new PromptTemplateId("project-title", 1);

    private final PromptRegistry promptRegistry;
    private final PromptRenderer promptRenderer;
    private final AiInputSnapshotHasher snapshotHasher;
    private final ProjectTitleOutputDefinition outputDefinition;

    public ProjectTitlePromptFactory(
            PromptRegistry promptRegistry,
            PromptRenderer promptRenderer,
            AiInputSnapshotHasher snapshotHasher,
            ProjectTitleOutputDefinition outputDefinition
    ) {
        this.promptRegistry = promptRegistry;
        this.promptRenderer = promptRenderer;
        this.snapshotHasher = snapshotHasher;
        this.outputDefinition = outputDefinition;
    }

    public StructuredAiProviderRequest<ProjectTitleSuggestionOutput> providerRequest(
            ProjectTitleContext context
    ) {
        return providerRequest(context, PROMPT_ID.trackingValue());
    }

    public StructuredAiProviderRequest<ProjectTitleSuggestionOutput> providerRequest(
            ProjectTitleContext context,
            String promptVersion
    ) {
        if (context == null) {
            throw new IllegalArgumentException("프로젝트 제목 context는 필수입니다.");
        }
        RenderedPrompt prompt = promptRenderer.render(
                promptRegistry.get(PROMPT_ID, promptVersion),
                Map.of("projectInput", serialize(context)));
        return new StructuredAiProviderRequest<>(
                AiFeature.PROJECT_TITLE_SUGGESTION,
                prompt,
                outputDefinition.get(),
                StructuredOutputValidationContext.empty());
    }

    private String serialize(Object value) {
        return new String(
                snapshotHasher.canonicalSnapshot(value).canonicalBytes(),
                StandardCharsets.UTF_8);
    }
}
