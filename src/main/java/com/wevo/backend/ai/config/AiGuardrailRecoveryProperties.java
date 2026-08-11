package com.wevo.backend.ai.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wevo.ai.guardrails.recovery")
public record AiGuardrailRecoveryProperties(
        Boolean enabled,
        Duration interval,
        Duration orphanGrace,
        Integer batchSize
) {

    public AiGuardrailRecoveryProperties {
        enabled = enabled == null ? Boolean.TRUE : enabled;
        interval = interval == null ? Duration.ofSeconds(60) : interval;
        orphanGrace = orphanGrace == null ? Duration.ofMinutes(5) : orphanGrace;
        batchSize = batchSize == null ? 100 : batchSize;
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("wevo.ai.guardrails.recovery.interval은 0보다 커야 합니다.");
        }
        if (orphanGrace.isZero() || orphanGrace.isNegative()) {
            throw new IllegalArgumentException("wevo.ai.guardrails.recovery.orphan-grace는 0보다 커야 합니다.");
        }
        if (batchSize <= 0 || batchSize > 1000) {
            throw new IllegalArgumentException(
                    "wevo.ai.guardrails.recovery.batch-size는 1 이상 1000 이하여야 합니다.");
        }
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }
}
