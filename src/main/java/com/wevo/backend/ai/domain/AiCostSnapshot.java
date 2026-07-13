package com.wevo.backend.ai.domain;

import java.math.BigDecimal;

public record AiCostSnapshot(
        String pricingVersion,
        BigDecimal inputPricePerMillionTokens,
        BigDecimal outputPricePerMillionTokens,
        BigDecimal cacheReadPricePerMillionTokens,
        BigDecimal cacheWritePricePerMillionTokens,
        BigDecimal estimatedCost
) {

    public static AiCostSnapshot unpriced(String pricingVersion) {
        return new AiCostSnapshot(pricingVersion, null, null, null, null, null);
    }
}
