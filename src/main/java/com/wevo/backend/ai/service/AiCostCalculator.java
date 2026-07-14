package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.AiUsageMetadata;
import com.wevo.backend.ai.config.AiPricingProperties;
import com.wevo.backend.ai.domain.AiCostSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class AiCostCalculator {

    private static final Logger log = LoggerFactory.getLogger(AiCostCalculator.class);
    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000L);
    private static final int COST_SCALE = 12;

    private final AiPricingProperties properties;

    public AiCostCalculator(AiPricingProperties properties) {
        this.properties = properties;
    }

    public AiCostSnapshot calculate(AiUsageMetadata usage) {
        if (usage == null || usage.modelId() == null) {
            return AiCostSnapshot.unpriced(properties.version());
        }

        AiPricingProperties.ModelPricing pricing = properties.models().get(usage.modelId());
        if (pricing == null) {
            log.warn("AI pricing is not configured for modelId={}", usage.modelId());
            return AiCostSnapshot.unpriced(properties.version());
        }

        BigDecimal estimatedCost = null;
        if (usage.inputTokens() != null && usage.outputTokens() != null) {
            estimatedCost = cost(usage.inputTokens(), pricing.inputPerMillionTokens())
                    .add(cost(usage.outputTokens(), pricing.outputPerMillionTokens()))
                    .add(cost(orZero(usage.cacheReadInputTokens()), pricing.cacheReadPerMillionTokens()))
                    .add(cost(orZero(usage.cacheWriteInputTokens()), pricing.cacheWritePerMillionTokens()))
                    .setScale(COST_SCALE, RoundingMode.HALF_UP);
        }

        return new AiCostSnapshot(
                properties.version(),
                pricing.inputPerMillionTokens(),
                pricing.outputPerMillionTokens(),
                pricing.cacheReadPerMillionTokens(),
                pricing.cacheWritePerMillionTokens(),
                estimatedCost
        );
    }

    private BigDecimal cost(long tokens, BigDecimal pricePerMillion) {
        return BigDecimal.valueOf(tokens).multiply(pricePerMillion).divide(ONE_MILLION, COST_SCALE, RoundingMode.HALF_UP);
    }

    private long orZero(Long value) {
        return value == null ? 0L : value;
    }
}
