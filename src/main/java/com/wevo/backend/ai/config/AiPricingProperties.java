package com.wevo.backend.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.Map;

@ConfigurationProperties(prefix = "wevo.ai.pricing")
public record AiPricingProperties(
        String version,
        Map<String, ModelPricing> models
) {

    public AiPricingProperties {
        version = version == null || version.isBlank() ? null : version;
        if (version != null && version.length() > 100) {
            throw new IllegalArgumentException("wevo.ai.pricing.version은 100자 이하여야 합니다.");
        }
        models = models == null ? Map.of() : Map.copyOf(models);
        models.forEach((modelId, pricing) -> pricing.validate(modelId));
    }

    public record ModelPricing(
            BigDecimal inputPerMillionTokens,
            BigDecimal outputPerMillionTokens,
            BigDecimal cacheReadPerMillionTokens,
            BigDecimal cacheWritePerMillionTokens
    ) {

        private void validate(String modelId) {
            validateRate(modelId, "input-per-million-tokens", inputPerMillionTokens);
            validateRate(modelId, "output-per-million-tokens", outputPerMillionTokens);
            validateRate(modelId, "cache-read-per-million-tokens", cacheReadPerMillionTokens);
            validateRate(modelId, "cache-write-per-million-tokens", cacheWritePerMillionTokens);
        }

        private void validateRate(String modelId, String key, BigDecimal value) {
            if (value == null || value.signum() < 0) {
                throw new IllegalArgumentException(
                        "wevo.ai.pricing.models." + modelId + "." + key + "는 0 이상이어야 합니다."
                );
            }
        }
    }
}
