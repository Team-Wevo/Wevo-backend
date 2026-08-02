package com.wevo.backend.ai.config;

import com.wevo.backend.ai.domain.AiFeature;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiGuardrailPropertiesTest {

    @Test
    void enabledPolicyRequiresEveryFeatureAndPositiveLimits() {
        assertThatThrownBy(() -> new AiGuardrailProperties(
                true,
                "v1",
                new AiGuardrailProperties.QuotaLimits(1, 10),
                new AiGuardrailProperties.QuotaLimits(1, 10),
                Map.of(),
                new AiGuardrailProperties.CostLimits(
                        new BigDecimal("1"), new BigDecimal("10"), new BigDecimal("1"))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("모든 AI 기능");
    }

    @Test
    void rejectsNegativeOverflowAndInvalidCostUnit() {
        assertThatThrownBy(() -> properties(
                new AiGuardrailProperties.QuotaLimits(-1, 10),
                new AiGuardrailProperties.CostLimits(
                        new BigDecimal("1"), new BigDecimal("10"), new BigDecimal("1"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("per-minute");

        assertThatThrownBy(() -> properties(
                new AiGuardrailProperties.QuotaLimits(1, 10),
                new AiGuardrailProperties.CostLimits(
                        new BigDecimal("1.0000001"), new BigDecimal("10"), new BigDecimal("1"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("소수 6자리");
    }

    private AiGuardrailProperties properties(
            AiGuardrailProperties.QuotaLimits user,
            AiGuardrailProperties.CostLimits cost
    ) {
        Map<String, AiGuardrailProperties.FeatureLimits> features = Arrays.stream(AiFeature.values())
                .collect(Collectors.toMap(
                        AiFeature::configKey,
                        ignored -> new AiGuardrailProperties.FeatureLimits(10, 100, 1)));
        return new AiGuardrailProperties(
                true,
                "v1",
                user,
                new AiGuardrailProperties.QuotaLimits(10, 100),
                features,
                cost
        );
    }
}
