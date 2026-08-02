package com.wevo.backend.ai.config;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiGuardrailStartupValidatorTest {

    @Test
    void productionProviderCannotStartWithDisabledGuardrails() {
        AiGuardrailStartupValidator validator = new AiGuardrailStartupValidator(
                aiProperties(),
                new AiGuardrailProperties(false, null, null, null, null, null),
                pricing());

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("enabled=true");
    }

    @Test
    void productionProviderCannotStartWithoutCurrentModelPricing() {
        AiGuardrailStartupValidator validator = new AiGuardrailStartupValidator(
                aiProperties(),
                enabledGuardrails(),
                new AiPricingProperties("v1", Map.of()));

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pricing snapshot");
    }

    private AiProperties aiProperties() {
        return new AiProperties(
                "openai",
                new AiProperties.ModelOptions(
                        "ignored", Duration.ofSeconds(1), 100, 10, 1000, 100,
                        AiProperties.ModelOptions.CONSERVATIVE_CHAR_V1,
                        AiProperties.ModelOptions.REJECT_OVERSIZED_INPUT_V1,
                        0, Duration.ZERO, Duration.ZERO),
                Map.of(),
                new AiProperties.StructuredOutputOptions(0),
                new AiProperties.OpenAiOptions(
                        "test-key", "https://api.openai.com", "test-model",
                        Duration.ofSeconds(1), 10, "medium", 1000));
    }

    private AiGuardrailProperties enabledGuardrails() {
        Map<String, AiGuardrailProperties.FeatureLimits> features = Arrays.stream(
                        com.wevo.backend.ai.domain.AiFeature.values())
                .collect(Collectors.toMap(
                        com.wevo.backend.ai.domain.AiFeature::configKey,
                        ignored -> new AiGuardrailProperties.FeatureLimits(10, 100, 1)));
        return new AiGuardrailProperties(
                true, "v1",
                new AiGuardrailProperties.QuotaLimits(10, 100),
                new AiGuardrailProperties.QuotaLimits(10, 100),
                features,
                new AiGuardrailProperties.CostLimits(
                        BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE));
    }

    private AiPricingProperties pricing() {
        return new AiPricingProperties(
                "v1",
                Map.of("test-model", new AiPricingProperties.ModelPricing(
                        BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE)));
    }
}
