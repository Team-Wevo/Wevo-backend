package com.wevo.backend.ai.prompt;

import com.wevo.backend.ai.client.StructuredAiProviderRequest;
import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.client.StructuredPromptFormatter;
import com.wevo.backend.ai.context.AiInputSnapshotHasher;
import com.wevo.backend.ai.context.AiTokenBudgetInput;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.dto.model.OpinionClusteringOutput;
import com.wevo.backend.ai.service.OpinionClusteringOutputDefinition;
import com.wevo.backend.ai.service.OpinionClusteringPromptContext;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 의견 분류 context를 prompt와 strict output 계약으로 변환한다. */
@Component
public class OpinionClusteringPromptFactory {

    public static final PromptTemplateId PROMPT_ID =
            new PromptTemplateId("opinion-clustering", 1);

    private final PromptRegistry promptRegistry;
    private final PromptRenderer promptRenderer;
    private final AiInputSnapshotHasher snapshotHasher;
    private final OpinionClusteringOutputDefinition outputDefinition;

    public OpinionClusteringPromptFactory(
            PromptRegistry promptRegistry,
            PromptRenderer promptRenderer,
            AiInputSnapshotHasher snapshotHasher,
            OpinionClusteringOutputDefinition outputDefinition
    ) {
        this.promptRegistry = promptRegistry;
        this.promptRenderer = promptRenderer;
        this.snapshotHasher = snapshotHasher;
        this.outputDefinition = outputDefinition;
    }

    public StructuredAiProviderRequest<OpinionClusteringOutput> providerRequest(
            OpinionClusteringPromptContext context,
            Set<Long> allowedOpinionIds
    ) {
        RenderedPrompt prompt = promptRenderer.render(
                promptRegistry.get(PROMPT_ID),
                Map.of("clusteringContext", serialize(context))
        );
        return new StructuredAiProviderRequest<>(
                AiFeature.OPINION_CLUSTERING,
                prompt,
                outputDefinition.get(),
                new StructuredOutputValidationContext(allowedOpinionIds)
        );
    }

    public AiTokenBudgetInput tokenBudgetInput(
            OpinionClusteringPromptContext context,
            Set<Long> allowedOpinionIds
    ) {
        StructuredAiProviderRequest<OpinionClusteringOutput> request =
                providerRequest(context, allowedOpinionIds);
        return AiTokenBudgetInput.of(
                request.prompt().systemPrompt(),
                StructuredPromptFormatter.initialUserPrompt(request)
        );
    }

    private String serialize(OpinionClusteringPromptContext context) {
        return new String(
                snapshotHasher.canonicalSnapshot(context).canonicalBytes(),
                StandardCharsets.UTF_8
        );
    }
}
