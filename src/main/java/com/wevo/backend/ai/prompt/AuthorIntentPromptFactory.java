package com.wevo.backend.ai.prompt;

import com.wevo.backend.ai.client.StructuredAiProviderRequest;
import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.context.AiInputSnapshotHasher;
import com.wevo.backend.ai.context.AuthorIntentContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.dto.model.AuthorIntentExtractionOutput;
import com.wevo.backend.ai.service.AuthorIntentExtractionOutputDefinition;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AuthorIntentPromptFactory {

    public static final PromptTemplateId PROMPT_ID = new PromptTemplateId("author-intent", 1);

    private final PromptRegistry promptRegistry;
    private final PromptRenderer promptRenderer;
    private final AiInputSnapshotHasher snapshotHasher;
    private final AuthorIntentExtractionOutputDefinition outputDefinition;

    public AuthorIntentPromptFactory(
            PromptRegistry promptRegistry,
            PromptRenderer promptRenderer,
            AiInputSnapshotHasher snapshotHasher,
            AuthorIntentExtractionOutputDefinition outputDefinition
    ) {
        this.promptRegistry = promptRegistry;
        this.promptRenderer = promptRenderer;
        this.snapshotHasher = snapshotHasher;
        this.outputDefinition = outputDefinition;
    }

    public StructuredAiProviderRequest<AuthorIntentExtractionOutput> providerRequest(
            AuthorIntentContext context
    ) {
        return providerRequest(context, PROMPT_ID.trackingValue());
    }

    public StructuredAiProviderRequest<AuthorIntentExtractionOutput> providerRequest(
            AuthorIntentContext context,
            String promptVersion
    ) {
        if (context == null) {
            throw new IllegalArgumentException("작성자 의도 context는 필수입니다.");
        }
        RenderedPrompt prompt = promptRenderer.render(
                promptRegistry.get(PROMPT_ID, promptVersion),
                Map.of("sectionData", serialize(context)));
        return new StructuredAiProviderRequest<>(
                AiFeature.AUTHOR_INTENT_EXTRACTION,
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
