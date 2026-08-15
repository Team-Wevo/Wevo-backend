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
                Map.of("projectInput", serialize(new PromptPayload(
                        context.ideaText(), context.resultType(), context.audience()))));
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

    /**
     * 프롬프트에 싣는 값만 담는다 — 제목 생성에 필요 없는 {@code projectId} 등 내부 식별자는
     * AI provider 에 보내지 않는다. (CLAUDE.md §7 — 필요한 데이터만)
     */
    private record PromptPayload(String ideaText, String resultType, String audience) {
    }
}
