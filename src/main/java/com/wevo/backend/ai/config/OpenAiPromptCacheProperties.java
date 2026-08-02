package com.wevo.backend.ai.config;

import com.wevo.backend.ai.domain.AiFeature;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.Set;

/** OpenAI prompt caching 실험과 즉시 rollback을 위한 기능별 설정. */
@ConfigurationProperties(prefix = "wevo.ai.openai.prompt-cache")
public record OpenAiPromptCacheProperties(
        Boolean optimizationEnabled,
        Set<String> explicitFeatures,
        Set<String> explicitModels,
        String ttl
) {

    public OpenAiPromptCacheProperties {
        optimizationEnabled = Boolean.TRUE.equals(optimizationEnabled);
        explicitFeatures = explicitFeatures == null ? Set.of() : Set.copyOf(explicitFeatures);
        explicitModels = explicitModels == null ? Set.of() : Set.copyOf(explicitModels);
        ttl = ttl == null || ttl.isBlank() ? "30m" : ttl.strip();

        Set<String> supportedFeatures = new HashSet<>();
        for (AiFeature feature : AiFeature.values()) {
            supportedFeatures.add(feature.configKey());
        }
        if (!supportedFeatures.containsAll(explicitFeatures)) {
            throw new IllegalArgumentException(
                    "wevo.ai.openai.prompt-cache.explicit-features에 지원하지 않는 기능 key가 있습니다."
            );
        }
        if (explicitModels.stream().anyMatch(model -> model == null
                || model.isBlank() || model.length() > 100)) {
            throw new IllegalArgumentException(
                    "wevo.ai.openai.prompt-cache.explicit-models는 1자 이상 100자 이하여야 합니다."
            );
        }
        if (!"30m".equals(ttl)) {
            throw new IllegalArgumentException(
                    "wevo.ai.openai.prompt-cache.ttl은 현재 OpenAI가 지원하는 30m이어야 합니다."
            );
        }
    }

    public boolean isOptimizationEnabled() {
        return Boolean.TRUE.equals(optimizationEnabled);
    }

    public boolean requestsExplicit(AiFeature feature) {
        return feature != null && explicitFeatures.contains(feature.configKey());
    }

    public boolean supportsExplicitModel(String modelId) {
        return modelId != null && explicitModels.contains(modelId);
    }

    public String profileId() {
        if (!isOptimizationEnabled()) {
            return "implicit-baseline";
        }
        return explicitFeatures.isEmpty()
                ? "implicit-key-candidate"
                : "explicit-candidate";
    }
}
