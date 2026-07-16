package com.wevo.backend.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "wevo.ai.audit")
public record AiAuditProperties(
        boolean recoveryEnabled,
        Duration recoveryInterval,
        Duration orphanGrace
) {

    public AiAuditProperties {
        recoveryInterval = recoveryInterval == null ? Duration.ofSeconds(60) : recoveryInterval;
        orphanGrace = orphanGrace == null ? Duration.ofSeconds(30) : orphanGrace;
        if (recoveryInterval.isZero() || recoveryInterval.isNegative()) {
            throw new IllegalArgumentException("wevo.ai.audit.recovery-interval은 0보다 커야 합니다.");
        }
        if (orphanGrace.isNegative()) {
            throw new IllegalArgumentException("wevo.ai.audit.orphan-grace는 0 이상이어야 합니다.");
        }
    }
}
