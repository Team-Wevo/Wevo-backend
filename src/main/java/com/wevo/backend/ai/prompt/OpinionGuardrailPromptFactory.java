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
                Map.of("guardrailInput", xmlEscape(serialize(new PromptPayload(
                        context.sectionTitle(), context.sectionGuide(), context.content())))));
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
     * {@code <data>} 경계 안에 들어가는 값의 XML 특수문자를 이스케이프한다. 사용자 입력(content 등)에
     * {@code </data>}가 있으면 신뢰 불가 데이터 경계가 끊겨 프롬프트 인젝션이 가능하므로,
     * {@code &}·{@code <}·{@code >}를 엔티티로 치환해 경계를 종료할 수 없게 한다.
     */
    private static String xmlEscape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /**
     * 프롬프트에 싣는 값만 담는다 — 판정에 불필요한 sectionId 등 내부 식별자는 provider 에 보내지
     * 않는다. (CLAUDE.md §7 — 필요한 데이터만)
     */
    private record PromptPayload(String sectionTitle, String sectionGuide, String content) {
    }
}
