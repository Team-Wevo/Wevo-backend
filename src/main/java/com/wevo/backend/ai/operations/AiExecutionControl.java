package com.wevo.backend.ai.operations;

import com.wevo.backend.ai.config.AiOperationsProperties;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.exception.AiProviderException;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;

/** 전역/기능 kill switch와 비용이 제한된 provider circuit breaker. */
@Component
public class AiExecutionControl {

    private static final EnumSet<ErrorCode> CIRCUIT_FAILURES = EnumSet.of(
            ErrorCode.AI_RATE_LIMITED,
            ErrorCode.AI_PROVIDER_TIMEOUT,
            ErrorCode.AI_PROVIDER_OVERLOADED,
            ErrorCode.AI_PROVIDER_UNAVAILABLE
    );

    private final AiOperationsProperties properties;
    private final Clock clock;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicInteger halfOpenCalls = new AtomicInteger();
    private final AtomicInteger halfOpenSubmissions = new AtomicInteger();
    private volatile AiCircuitState state = AiCircuitState.CLOSED;
    private volatile Instant openedAt;

    public AiExecutionControl(AiOperationsProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void requireSubmissionAllowed(AiFeature feature) {
        requireEnabled(feature);
        transitionToHalfOpenIfDue();
        if (state == AiCircuitState.OPEN) {
            throw unavailable();
        }
        if (state == AiCircuitState.HALF_OPEN && halfOpenSubmissions.incrementAndGet() > 1) {
            halfOpenSubmissions.decrementAndGet();
            throw unavailable();
        }
    }

    public void requireDispatchAllowed(AiFeature feature) {
        requireEnabled(feature);
        transitionToHalfOpenIfDue();
        if (state == AiCircuitState.OPEN) {
            throw unavailable();
        }
    }

    public synchronized void beforeProviderCall(AiFeature feature) {
        requireEnabled(feature);
        transitionToHalfOpenIfDue();
        if (state == AiCircuitState.OPEN) {
            throw providerUnavailable();
        }
        if (state == AiCircuitState.HALF_OPEN
                && halfOpenCalls.incrementAndGet() > properties.circuitBreaker().halfOpenMaxCalls()) {
            halfOpenCalls.decrementAndGet();
            throw providerUnavailable();
        }
    }

    public synchronized void recordSuccess() {
        consecutiveFailures.set(0);
        halfOpenCalls.set(0);
        halfOpenSubmissions.set(0);
        state = AiCircuitState.CLOSED;
        openedAt = null;
    }

    public synchronized void recordFailure(Throwable throwable) {
        if (state == AiCircuitState.HALF_OPEN) {
            halfOpenCalls.updateAndGet(value -> Math.max(0, value - 1));
            halfOpenSubmissions.updateAndGet(value -> Math.max(0, value - 1));
        }
        if (!countsForCircuit(throwable)) {
            return;
        }
        int failures = consecutiveFailures.incrementAndGet();
        if (state == AiCircuitState.HALF_OPEN
                || failures >= properties.circuitBreaker().failureThreshold()) {
            state = AiCircuitState.OPEN;
            openedAt = Instant.now(clock);
            halfOpenCalls.set(0);
            halfOpenSubmissions.set(0);
        }
    }

    public AiCircuitState state() {
        transitionToHalfOpenIfDue();
        return state;
    }

    public boolean enabledFor(AiFeature feature) {
        return properties.enabledFor(feature);
    }

    private void requireEnabled(AiFeature feature) {
        if (!properties.enabledFor(feature)) {
            throw unavailable();
        }
    }

    private synchronized void transitionToHalfOpenIfDue() {
        if (state == AiCircuitState.OPEN && openedAt != null
                && !Instant.now(clock).isBefore(openedAt.plus(properties.circuitBreaker().openDuration()))) {
            state = AiCircuitState.HALF_OPEN;
            halfOpenCalls.set(0);
            halfOpenSubmissions.set(0);
        }
    }

    private boolean countsForCircuit(Throwable throwable) {
        return throwable instanceof AiProviderException exception
                && CIRCUIT_FAILURES.contains(exception.getErrorCode());
    }

    private BusinessException unavailable() {
        return new BusinessException(ErrorCode.AI_PROVIDER_UNAVAILABLE);
    }

    private AiProviderException providerUnavailable() {
        return new AiProviderException(
                ErrorCode.AI_PROVIDER_UNAVAILABLE,
                new IllegalStateException("AI provider circuit is open"));
    }
}
