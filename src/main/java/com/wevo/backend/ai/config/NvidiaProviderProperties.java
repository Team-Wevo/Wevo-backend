package com.wevo.backend.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;
import java.util.Set;

@ConfigurationProperties(prefix = "wevo.ai.nvidia")
public record NvidiaProviderProperties(Double temperature, String reasoningEffort) {

    private static final Set<String> SUPPORTED_REASONING_EFFORTS = Set.of("none", "high");

    public NvidiaProviderProperties {
        temperature = temperature == null ? 0.1d : temperature;
        if (temperature < 0.0d || temperature > 1.0d) {
            throw new IllegalArgumentException("wevo.ai.nvidia.temperature는 0 이상 1 이하여야 합니다.");
        }
        reasoningEffort = reasoningEffort == null || reasoningEffort.isBlank()
                ? "none"
                : reasoningEffort.toLowerCase(Locale.ROOT);
        if (!SUPPORTED_REASONING_EFFORTS.contains(reasoningEffort)) {
            throw new IllegalArgumentException("wevo.ai.nvidia.reasoning-effort는 none 또는 high여야 합니다.");
        }
    }
}
