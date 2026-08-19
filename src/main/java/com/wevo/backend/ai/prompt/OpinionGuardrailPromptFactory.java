package com.wevo.backend.ai.prompt;

import com.wevo.backend.ai.client.StructuredAiProviderRequest;
import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.context.AiInputSnapshotHasher;
import com.wevo.backend.ai.context.OpinionGuardrailContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.dto.model.OpinionGuardrailVerdict;
import com.wevo.backend.ai.service.OpinionGuardrailOutputDefinition;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OpinionGuardrailPromptFactory {

    public static final PromptTemplateId PROMPT_ID = new PromptTemplateId("opinion-guardrail", 1);

    private final PromptRegistry promptRegistry;
    private final PromptRenderer promptRenderer;
    private final AiInputSnapshotHasher snapshotHasher;
    private final OpinionGuardrailOutputDefinition outputDefinition;

    public OpinionGuardrailPromptFactory(
            PromptRegistry promptRegistry,
            PromptRenderer promptRenderer,
            AiInputSnapshotHasher snapshotHasher,
            OpinionGuardrailOutputDefinition outputDefinition
    ) {
        this.promptRegistry = promptRegistry;
        this.promptRenderer = promptRenderer;
        this.snapshotHasher = snapshotHasher;
        this.outputDefinition = outputDefinition;
    }

    public StructuredAiProviderRequest<OpinionGuardrailVerdict> providerRequest(
            OpinionGuardrailContext context
    ) {
        return providerRequest(context, PROMPT_ID.trackingValue());
    }

    public StructuredAiProviderRequest<OpinionGuardrailVerdict> providerRequest(
            OpinionGuardrailContext context,
            String promptVersion
    ) {
        if (context == null) {
            throw new IllegalArgumentException("의견 가드레일 context는 필수입니다.");
        }
        RenderedPrompt prompt = promptRenderer.render(
                promptRegistry.get(PROMPT_ID, promptVersion),
                Map.of("guardrailInput", serialize(new PromptPayload(
                        context.sectionTitle(), context.sectionGuide(), context.content()))));
        return new StructuredAiProviderRequest<>(
                AiFeature.OPINION_CONTENT_GUARDRAIL,
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
     * 프롬프트에 싣는 값만 담는다 — 판정에 불필요한 sectionId 등 내부 식별자는 provider 에 보내지
     * 않는다. (CLAUDE.md §7 — 필요한 데이터만)
     */
    private record PromptPayload(String sectionTitle, String sectionGuide, String content) {
    }
}
