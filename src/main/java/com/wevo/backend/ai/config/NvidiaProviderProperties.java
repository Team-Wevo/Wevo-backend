package com.wevo.backend.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wevo.ai.nvidia")
public record NvidiaProviderProperties(Double temperature) {

    public NvidiaProviderProperties {
        temperature = temperature == null ? 1.0d : temperature;
        if (temperature < 0.0d || temperature > 1.0d) {
            throw new IllegalArgumentException("wevo.ai.nvidia.temperature는 0 이상 1 이하여야 합니다.");
        }
    }
}
