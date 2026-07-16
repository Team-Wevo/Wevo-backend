package com.wevo.backend.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wevo.ai.jobs")
public record AiJobProperties(Integer maxExecutionsPerKey) {

    public AiJobProperties {
        maxExecutionsPerKey = maxExecutionsPerKey == null ? 3 : maxExecutionsPerKey;
        if (maxExecutionsPerKey <= 0) {
            throw new IllegalArgumentException("wevo.ai.jobs.max-executions-per-key는 1 이상이어야 합니다.");
        }
    }
}
