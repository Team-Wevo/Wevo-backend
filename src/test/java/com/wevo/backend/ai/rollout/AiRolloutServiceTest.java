package com.wevo.backend.ai.rollout;

import com.wevo.backend.ai.config.AiGuardrailProperties;
import com.wevo.backend.ai.config.AiPricingProperties;
import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.config.AiRolloutProperties;
import com.wevo.backend.ai.config.NvidiaProviderProperties;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.prompt.PromptRegistry;
import com.wevo.backend.ai.service.AiJobIdempotencyInput;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AiRolloutServiceTest {

    @Test
    void approvedCanarySelectionIsStableForTheSameJobIdentity() {
        AiRolloutService service = service(
                properties(true, true, true, "draft-generation:v1"), pricing());
        service.afterPropertiesSet();
        AiJobIdempotencyInput input = input("draft-generation:v2", "draft-generation:v1");

        AiRolloutSelection first = service.select(input);
        AiRolloutSelection retry = service.select(input);

        assertThat(first).isEqualTo(retry);
        assertThat(first.rolloutId()).isEqualTo("candidate");
        assertThat(first.promptVersion()).isEqualTo("draft-generation:v1");
        assertThat(first.modelId()).isEqualTo("gpt-5.6-luna");
        assertThat(first.reasoningEffort()).isEqualTo("medium");
    }

    @Test
    void unreviewedCombinationCannotBeActivated() {
        AiRolloutService service = service(
                properties(true, true, false, "draft-generation:v1"), pricing());

        assertThatThrownBy(service::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("사람 리뷰");
    }

    @Test
    void missingPricingAndWrongSchemaFailBeforeSelection() {
        AiRolloutService missingPricing = service(
                properties(true, true, true, "draft-generation:v1"),
                new AiPricingProperties("openai-2026-07-31-standard", Map.of()));
        assertThatThrownBy(missingPricing::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pricing");

        AiRolloutService wrongSchema = service(
                properties(true, true, true, "draft-generation:v1", "draft-generation:v2"),
                pricing());
        wrongSchema.afterPropertiesSet();
        assertThatThrownBy(() -> wrongSchema.select(
                input("draft-generation:v2", "draft-generation:v1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("schema version");
    }

    private AiRolloutService service(
            AiRolloutProperties rollout,
            AiPricingProperties pricing
    ) {
        AiProperties ai = mock(AiProperties.class);
        AiProperties.OpenAiOptions openai = mock(AiProperties.OpenAiOptions.class);
        given(ai.provider()).willReturn("openai");
        given(ai.openai()).willReturn(openai);
        given(openai.reasoningEffort()).willReturn("medium");
        AiGuardrailProperties guardrails = mock(AiGuardrailProperties.class);
        given(guardrails.isEnabled()).willReturn(true);
        given(guardrails.policyVersion()).willReturn("ai-guardrail-2026-08-02-v1");
        return new AiRolloutService(
                rollout, ai, new NvidiaProviderProperties(0.1d, "none"), pricing,
                guardrails, mock(PromptRegistry.class));
    }

    private AiRolloutProperties properties(
            boolean enabled,
            boolean evaluated,
            boolean reviewed,
            String candidatePrompt
    ) {
        return properties(enabled, evaluated, reviewed, candidatePrompt, "draft-generation:v1");
    }

    private AiRolloutProperties properties(
            boolean enabled,
            boolean evaluated,
            boolean reviewed,
            String candidatePrompt,
            String candidateSchema
    ) {
        AiRolloutProperties.Combination stable = combination(
                "draft-generation:v2", "draft-generation:v1", true, true);
        AiRolloutProperties.Combination candidate = combination(
                candidatePrompt, candidateSchema, evaluated, reviewed);
        return new AiRolloutProperties(
                enabled,
                Map.of("stable", stable, "candidate", candidate),
                Map.of("draft-generation", new AiRolloutProperties.Route(
                        "stable", "candidate", 100, Set.of())));
    }

    private AiRolloutProperties.Combination combination(
            String prompt,
            String schema,
            boolean evaluated,
            boolean reviewed
    ) {
        return new AiRolloutProperties.Combination(
                "gpt-5.6-luna", prompt, schema, "medium",
                "openai-2026-07-31-standard", "ai-guardrail-2026-08-02-v1",
                evaluated, reviewed);
    }

    private AiPricingProperties pricing() {
        BigDecimal rate = new BigDecimal("0.20");
        return new AiPricingProperties(
                "openai-2026-07-31-standard",
                Map.of("gpt-5.6-luna", new AiPricingProperties.ModelPricing(
                        rate, rate, rate, rate)));
    }

    private AiJobIdempotencyInput input(String prompt, String schema) {
        return new AiJobIdempotencyInput(
                AiFeature.DRAFT_GENERATION, 10L, 20L, "a".repeat(64),
                "draft-source:v1", prompt, schema, "gpt-5.6-luna", 4096);
    }
}
