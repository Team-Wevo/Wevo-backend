package com.wevo.backend.ai.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

/**
 * AI 작업 실행 드라이버(폴러·실행기·heartbeat) 설정. 시작 시 불변식을 검증해 잘못된 설정으로
 * 조용히 오작동하지 않게 한다. (예: heartbeat 간격이 timeout보다 크면 첫 heartbeat 전에 회수될 수 있음)
 *
 * <p>{@code wevo.ai.jobs} 접두어를 {@link AiJobProperties}(max-executions-per-key)와 공유하되,
 * 각 record는 자신이 선언한 필드만 바인딩한다(알 수 없는 키 무시).
 */
@Validated
@ConfigurationProperties(prefix = "wevo.ai.jobs")
public record AiJobDispatchProperties(
        Boolean dispatchEnabled,
        Duration dispatchInterval,
        Integer dispatchBatchSize,
        Integer maxConcurrentJobs,
        Duration heartbeatInterval,
        Duration heartbeatTimeout,
        Duration recoveryInterval,
        Duration applicationTimeout
) {

    public AiJobDispatchProperties(
            Boolean dispatchEnabled,
            Duration dispatchInterval,
            Integer dispatchBatchSize,
            Integer maxConcurrentJobs,
            Duration heartbeatInterval,
            Duration heartbeatTimeout,
            Duration recoveryInterval
    ) {
        this(dispatchEnabled, dispatchInterval, dispatchBatchSize, maxConcurrentJobs,
                heartbeatInterval, heartbeatTimeout, recoveryInterval, null);
    }

    @ConstructorBinding
    public AiJobDispatchProperties {
        dispatchEnabled = dispatchEnabled == null ? Boolean.TRUE : dispatchEnabled;
        dispatchInterval = defaulted(dispatchInterval, Duration.ofSeconds(2));
        dispatchBatchSize = dispatchBatchSize == null ? 20 : dispatchBatchSize;
        maxConcurrentJobs = maxConcurrentJobs == null ? 8 : maxConcurrentJobs;
        heartbeatInterval = defaulted(heartbeatInterval, Duration.ofSeconds(15));
        heartbeatTimeout = defaulted(heartbeatTimeout, Duration.ofSeconds(60));
        recoveryInterval = defaulted(recoveryInterval, Duration.ofSeconds(60));
        applicationTimeout = defaulted(applicationTimeout, Duration.ofMinutes(15));

        requirePositive(dispatchBatchSize, "dispatch-batch-size");
        requirePositive(maxConcurrentJobs, "max-concurrent-jobs");
        requirePositiveDuration(dispatchInterval, "dispatch-interval");
        requirePositiveDuration(heartbeatInterval, "heartbeat-interval");
        requirePositiveDuration(heartbeatTimeout, "heartbeat-timeout");
        requirePositiveDuration(recoveryInterval, "recovery-interval");
        requirePositiveDuration(applicationTimeout, "application-timeout");
        if (heartbeatInterval.compareTo(heartbeatTimeout) >= 0) {
            throw new IllegalArgumentException(
                    "wevo.ai.jobs.heartbeat-interval은 heartbeat-timeout보다 작아야 합니다. "
                            + "(작업이 첫 heartbeat 전에 회수되는 것을 막기 위함)");
        }
        if (applicationTimeout.compareTo(heartbeatTimeout) <= 0) {
            throw new IllegalArgumentException(
                    "wevo.ai.jobs.application-timeout은 heartbeat-timeout보다 커야 합니다. "
                            + "(worker 사망 회수와 장기 실행 회수를 구분하기 위함)");
        }
    }

    private static Duration defaulted(Duration value, Duration fallback) {
        return value == null ? fallback : value;
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException("wevo.ai.jobs." + name + "는 1 이상이어야 합니다.");
        }
    }

    private static void requirePositiveDuration(Duration value, String name) {
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("wevo.ai.jobs." + name + "은 0보다 커야 합니다.");
        }
    }
}
