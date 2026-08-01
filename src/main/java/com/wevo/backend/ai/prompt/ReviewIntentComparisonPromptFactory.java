package com.wevo.backend.ai.prompt;

import com.wevo.backend.ai.client.StructuredAiProviderRequest;
import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.context.AiInputSnapshotHasher;
import com.wevo.backend.ai.context.ReviewIntentComparisonContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.dto.model.ReviewIntentComparisonOutput;
import com.wevo.backend.ai.service.ReviewIntentComparisonOutputDefinition;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ReviewIntentComparisonPromptFactory {

    public static final PromptTemplateId PROMPT_ID =
            new PromptTemplateId("review-intent-comparison", 1);

    private final PromptRegistry promptRegistry;
    private final PromptRenderer promptRenderer;
    private final AiInputSnapshotHasher snapshotHasher;
    private final ReviewIntentComparisonOutputDefinition outputDefinition;

    public ReviewIntentComparisonPromptFactory(
            PromptRegistry promptRegistry,
            PromptRenderer promptRenderer,
            AiInputSnapshotHasher snapshotHasher,
            ReviewIntentComparisonOutputDefinition outputDefinition
    ) {
        this.promptRegistry = promptRegistry;
        this.promptRenderer = promptRenderer;
        this.snapshotHasher = snapshotHasher;
        this.outputDefinition = outputDefinition;
    }

    public StructuredAiProviderRequest<ReviewIntentComparisonOutput> providerRequest(
            ReviewIntentComparisonContext context
    ) {
        if (context == null) {
            throw new IllegalArgumentException("검토 의도 비교 context는 필수입니다.");
        }
        RenderedPrompt prompt = promptRenderer.render(
                promptRegistry.get(PROMPT_ID),
                Map.of("comparisonData", serialize(new ComparisonData(
                        context.authorIntent(), context.reviewerSummary()))));
        return new StructuredAiProviderRequest<>(
                AiFeature.REVIEW_INTENT_COMPARISON,
                prompt,
                outputDefinition.get(),
                StructuredOutputValidationContext.forSourceContents(
                        context.reviewerSummary(),
                        Set.of(context.authorIntent(), context.reviewerSummary())));
    }

    private String serialize(Object value) {
        return new String(
                snapshotHasher.canonicalSnapshot(value).canonicalBytes(),
                StandardCharsets.UTF_8);
    }

    private record ComparisonData(String authorIntent, String reviewerSummary) {
    }
}
