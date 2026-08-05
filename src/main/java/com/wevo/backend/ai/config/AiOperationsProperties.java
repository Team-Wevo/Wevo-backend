package com.wevo.backend.ai.config;

import com.wevo.backend.ai.domain.AiFeature;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** AI 실행 격리와 bounded circuit breaker 정책. */
@ConfigurationProperties(prefix = "wevo.ai.operations")
public record AiOperationsProperties(
        Boolean enabled,
        Map<String, Boolean> features,
        CircuitBreaker circuitBreaker
) {

    public AiOperationsProperties {
        enabled = enabled == null || enabled;
        features = features == null ? Map.of() : Map.copyOf(features);
        Set<String> supported = new HashSet<>();
        for (AiFeature feature : AiFeature.values()) {
            supported.add(feature.configKey());
        }
        if (!supported.containsAll(features.keySet())) {
            throw new IllegalArgumentException("wevo.ai.operations.features에 지원하지 않는 기능 key가 있습니다.");
        }
        circuitBreaker = circuitBreaker == null ? CircuitBreaker.defaults() : circuitBreaker;
    }

    public boolean globallyEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public boolean enabledFor(AiFeature feature) {
        return globallyEnabled() && features.getOrDefault(feature.configKey(), true);
    }

    public record CircuitBreaker(
            Integer failureThreshold,
            Duration openDuration,
            Integer halfOpenMaxCalls
    ) {
        public CircuitBreaker {
            failureThreshold = failureThreshold == null ? 5 : failureThreshold;
            openDuration = openDuration == null ? Duration.ofSeconds(30) : openDuration;
            halfOpenMaxCalls = halfOpenMaxCalls == null ? 1 : halfOpenMaxCalls;
            if (failureThreshold < 1 || failureThreshold > 100) {
                throw new IllegalArgumentException("AI circuit failure-threshold는 1 이상 100 이하여야 합니다.");
            }
            if (openDuration.isZero() || openDuration.isNegative() || openDuration.compareTo(Duration.ofHours(1)) > 0) {
                throw new IllegalArgumentException("AI circuit open-duration은 0초 초과 1시간 이하여야 합니다.");
            }
            if (halfOpenMaxCalls < 1 || halfOpenMaxCalls > 10) {
                throw new IllegalArgumentException("AI circuit half-open-max-calls는 1 이상 10 이하여야 합니다.");
            }
        }

        static CircuitBreaker defaults() {
            return new CircuitBreaker(5, Duration.ofSeconds(30), 1);
        }
    }
}
