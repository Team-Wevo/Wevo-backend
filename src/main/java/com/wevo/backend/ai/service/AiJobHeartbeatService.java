package com.wevo.backend.ai.service;

import com.wevo.backend.ai.config.AiJobDispatchProperties;
import com.wevo.backend.ai.domain.AiFeature;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * RUNNING AI 작업의 heartbeat 등록·실행·해제를 한 경계에서 관리한다.
 *
 * <p>handler가 직접 scheduler를 다루면 heartbeat 등록 예외가 각 handler의 실패 경계 밖으로
 * 빠지거나, 갱신 예외가 periodic task를 영구 중단시키는 구현 차이가 생길 수 있다. 이 서비스는
 * 등록 예외는 호출자에게 전달하고, 개별 heartbeat 갱신 예외는 안전한 메타데이터만 기록한 뒤 다음
 * tick을 계속 실행한다.</p>
 */
@Component
public class AiJobHeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(AiJobHeartbeatService.class);
    private static final int FAILURE_WARNING_INTERVAL = 4;

    private final AiJobService aiJobService;
    private final ScheduledExecutorService scheduler;
    private final long heartbeatMillis;

    public AiJobHeartbeatService(
            AiJobService aiJobService,
            ScheduledExecutorService aiHeartbeatScheduler,
            AiJobDispatchProperties properties
    ) {
        this.aiJobService = aiJobService;
        this.scheduler = aiHeartbeatScheduler;
        this.heartbeatMillis = Math.max(1, properties.heartbeatInterval().toMillis());
    }

    /**
     * heartbeat를 등록한다. 등록 거부는 숨기지 않고 handler의 실패 경계로 전달한다.
     */
    public HeartbeatLease start(UUID requestId, AiFeature feature) {
        Objects.requireNonNull(requestId, "requestId는 필수입니다.");
        Objects.requireNonNull(feature, "feature는 필수입니다.");
        AtomicInteger consecutiveFailures = new AtomicInteger();
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> heartbeat(requestId, feature, consecutiveFailures),
                heartbeatMillis,
                heartbeatMillis,
                TimeUnit.MILLISECONDS
        );
        return new HeartbeatLease(future);
    }

    private void heartbeat(
            UUID requestId,
            AiFeature feature,
            AtomicInteger consecutiveFailures
    ) {
        try {
            aiJobService.heartbeat(requestId);
            int recoveredFailures = consecutiveFailures.getAndSet(0);
            if (recoveredFailures > 0) {
                log.info("AI 작업 heartbeat 갱신 복구 feature={} requestId={} previousFailures={}",
                        feature, requestId, recoveredFailures);
            }
        } catch (RuntimeException exception) {
            int failures = consecutiveFailures.incrementAndGet();
            if (failures == 1 || failures % FAILURE_WARNING_INTERVAL == 0) {
                log.warn("AI 작업 heartbeat 갱신 실패 feature={} requestId={} "
                                + "consecutiveFailures={} exceptionType={}",
                        feature, requestId, failures, exception.getClass().getSimpleName());
            }
        }
    }

    /** 등록된 periodic task를 handler 종료 시 정확히 한 번 취소한다. */
    public static final class HeartbeatLease implements AutoCloseable {

        private final ScheduledFuture<?> future;
        private final AtomicBoolean closed = new AtomicBoolean();

        private HeartbeatLease(ScheduledFuture<?> future) {
            this.future = future;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                future.cancel(false);
            }
        }
    }
}
