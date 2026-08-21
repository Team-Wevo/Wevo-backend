package com.wevo.backend.ai.prompt;

import com.wevo.backend.ai.client.StructuredAiProviderRequest;
import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.client.StructuredPromptFormatter;
import com.wevo.backend.ai.context.AiInputSnapshotHasher;
import com.wevo.backend.ai.context.AiTokenBudgetInput;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.service.SynthesisAiOutput;
import com.wevo.backend.ai.service.SynthesisOutputDefinition;
import com.wevo.backend.ai.service.SynthesisPromptContext;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** synthesis context를 prompt와 단일 구조화 출력 계약으로 변환한다. */
@Component
public class SynthesisPromptFactory {

    /** production 기본 계약(SynthesisContract)이 v3 으로 승격됨 — CONFLICT 작성 품질 지침 반영. */
    public static final PromptTemplateId PROMPT_ID = new PromptTemplateId("opinion-synthesis", 3);

    private final PromptRegistry promptRegistry;
    private final PromptRenderer promptRenderer;
    private final AiInputSnapshotHasher snapshotHasher;
    private final SynthesisOutputDefinition outputDefinition;

    public SynthesisPromptFactory(
            PromptRegistry promptRegistry,
            PromptRenderer promptRenderer,
            AiInputSnapshotHasher snapshotHasher,
            SynthesisOutputDefinition outputDefinition
    ) {
        this.promptRegistry = promptRegistry;
        this.promptRenderer = promptRenderer;
        this.snapshotHasher = snapshotHasher;
        this.outputDefinition = outputDefinition;
    }

    public StructuredAiProviderRequest<SynthesisAiOutput> providerRequest(
            SynthesisPromptContext context,
            Set<Long> allowedOpinionIds
    ) {
        return providerRequest(context, allowedOpinionIds, PROMPT_ID.trackingValue());
    }

    public StructuredAiProviderRequest<SynthesisAiOutput> providerRequest(
            SynthesisPromptContext context,
            Set<Long> allowedOpinionIds,
            String promptVersion
    ) {
        RenderedPrompt prompt = promptRenderer.render(
                promptRegistry.get(PROMPT_ID, promptVersion),
                Map.of("synthesisContext", serialize(context))
        );
        return new StructuredAiProviderRequest<>(
                AiFeature.OPINION_SYNTHESIS,
                prompt,
                outputDefinition.get(),
                new StructuredOutputValidationContext(allowedOpinionIds)
        );
    }

    public AiTokenBudgetInput tokenBudgetInput(
            SynthesisPromptContext context,
            Set<Long> allowedOpinionIds
    ) {
        return tokenBudgetInput(context, allowedOpinionIds, PROMPT_ID.trackingValue());
    }

    public AiTokenBudgetInput tokenBudgetInput(
            SynthesisPromptContext context,
            Set<Long> allowedOpinionIds,
            String promptVersion
    ) {
        StructuredAiProviderRequest<SynthesisAiOutput> request =
                providerRequest(context, allowedOpinionIds, promptVersion);
        return StructuredPromptFormatter.tokenBudgetInput(request, true);
    }

    private String serialize(SynthesisPromptContext context) {
        return new String(
                snapshotHasher.canonicalSnapshot(context).canonicalBytes(),
                StandardCharsets.UTF_8
        );
    }
}
