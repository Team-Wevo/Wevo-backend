package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.AiUsageMetadata;
import com.wevo.backend.ai.config.AiPricingProperties;
import com.wevo.backend.ai.domain.AiCostSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiCostCalculatorTest {

    @Test
    void includesAllTokenTypesAndPreservesSmallCost() {
        AiCostCalculator calculator = calculator(Map.of(
                "model-1",
                new AiPricingProperties.ModelPricing(
                        new BigDecimal("1.00"),
                        new BigDecimal("2.00"),
                        new BigDecimal("0.10"),
                        new BigDecimal("1.25")
                )
        ));

        AiCostSnapshot cost = calculator.calculate(
                new AiUsageMetadata("request", "model-1", 1L, 1L, 1L, 1L)
        );

        assertThat(cost.estimatedCost()).isEqualByComparingTo("0.000004350000");
        assertThat(cost.pricingVersion()).isEqualTo("pricing-v1");
    }

    @Test
    void unknownPricingOrMissingUsageDoesNotBecomeZeroCost() {
        AiCostCalculator calculator = calculator(Map.of(
                "model-1",
                new AiPricingProperties.ModelPricing(
                        BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE
                )
        ));

        assertThat(calculator.calculate(
                new AiUsageMetadata(null, "unknown", 1L, 1L, null, null)
        ).estimatedCost()).isNull();
        assertThat(calculator.calculate(
                new AiUsageMetadata(null, "unknown", null, null, null, null)
        ).estimatedCost()).isNull();
        assertThat(calculator.calculate(
                new AiUsageMetadata(null, "model-1", 1L, 1L, null, 0L)
        ).estimatedCost()).isNull();
    }

    private AiCostCalculator calculator(Map<String, AiPricingProperties.ModelPricing> models) {
        return new AiCostCalculator(new AiPricingProperties("pricing-v1", models));
    }
}
