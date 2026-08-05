package com.wevo.backend.ai.operations;

import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.repository.AiJobRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/** DB 상태를 읽기만 하는 queue/RUNNING gauge. Provider 호출을 수행하지 않는다. */
@Component
public class AiJobMetricsBinder implements MeterBinder {

    private final AiJobRepository repository;
    private final Clock clock;

    public AiJobMetricsBinder(AiJobRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("wevo.ai.jobs", repository, ignored -> safeCount(AiJobStatus.QUEUED))
                .tag("status", "queued").register(registry);
        Gauge.builder("wevo.ai.jobs", repository, ignored -> safeCount(AiJobStatus.RUNNING))
                .tag("status", "running").register(registry);
        Gauge.builder("wevo.ai.jobs.oldest_running.age", repository, ignored -> oldestRunningAge())
                .baseUnit("seconds").register(registry);
    }

    private double safeCount(AiJobStatus status) {
        try {
            return repository.countByStatus(status);
        } catch (RuntimeException ignored) {
            return Double.NaN;
        }
    }

    private double oldestRunningAge() {
        try {
            return repository.findFirstByStatusOrderByStartedAtAsc(AiJobStatus.RUNNING)
                    .map(job -> Math.max(0L, Duration.between(
                            job.getStartedAt(), LocalDateTime.now(clock)).toSeconds()))
                    .orElse(0L);
        } catch (RuntimeException ignored) {
            return Double.NaN;
        }
    }
}
