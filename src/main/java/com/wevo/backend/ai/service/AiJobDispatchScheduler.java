package com.wevo.backend.ai.service;

import com.wevo.backend.ai.config.AiJobDispatchProperties;
import com.wevo.backend.ai.domain.AiFeature;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * AI 작업의 <b>DB 기반 실행 드라이버</b>. 실행 가능한(핸들러 등록된) 기능의 QUEUED 작업을 가용 실행
 * 슬롯만큼 뽑아 실행하고, heartbeat가 끊긴 RUNNING 작업을 회수한다.
 *
 * <p>인메모리 제출에 의존하지 않으므로 프로세스 종료·재배포·인스턴스 재시작에도 QUEUED 작업이
 * 유실되지 않는다. 작업 claim은 {@link AiJobService#start}의 원자적 전이가 담당하므로 여러 인스턴스가
 * 같은 작업을 뽑아도 실제 실행·AI 호출은 한 번만 일어난다.
 *
 * <p><b>회수는 heartbeat 기준이며 실패 전이 직전에 조건을 원자적으로 재확인</b>한다
 * ({@link AiJobService#recoverIfHeartbeatStale}) — 조회와 실패 사이에 worker가 heartbeat를 갱신한
 * 살아 있는 작업을 오회수하지 않는다.
 */
@Component
@ConditionalOnProperty(
        prefix = "wevo.ai.jobs",
        name = "dispatch-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AiJobDispatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(AiJobDispatchScheduler.class);

    private final AiJobService aiJobService;
    private final AiJobExecutor aiJobExecutor;
    private final AiJobDispatchProperties properties;
    private final Clock clock;

    public AiJobDispatchScheduler(AiJobService aiJobService,
                                  AiJobExecutor aiJobExecutor,
                                  AiJobDispatchProperties properties,
                                  Clock aiClock) {
        this.aiJobService = aiJobService;
        this.aiJobExecutor = aiJobExecutor;
        this.properties = properties;
        this.clock = aiClock;
    }

    /** 실행 가능한 기능의 큐잉 작업을 <b>가용 실행 슬롯 수만큼만</b> 뽑아 실행 스레드로 넘긴다. */
    @Scheduled(
            fixedDelayString = "${wevo.ai.jobs.dispatch-interval:2s}",
            initialDelayString = "${wevo.ai.jobs.dispatch-interval:2s}"
    )
    public void dispatchQueued() {
        Set<AiFeature> features = aiJobExecutor.supportedFeatures();
        if (features.isEmpty()) {
            return;
        }
        int slots = aiJobExecutor.availableSlots();
        if (slots <= 0) {
            return;
        }
        int limit = Math.min(properties.dispatchBatchSize(), slots);
        for (UUID requestId : aiJobService.findNextQueuedRequestIds(features, limit)) {
            aiJobExecutor.dispatch(requestId);
        }
    }

    /** heartbeat가 끊긴 RUNNING 작업(크래시 등)을 실패로 정리한다(실패 직전 조건 재확인). */
    @Scheduled(
            fixedDelayString = "${wevo.ai.jobs.recovery-interval:60s}",
            initialDelayString = "${wevo.ai.jobs.recovery-interval:60s}"
    )
    public void recoverStalledRunning() {
        LocalDateTime threshold = LocalDateTime.now(clock).minus(properties.heartbeatTimeout());
        for (UUID requestId : aiJobService.findHeartbeatTimedOutRequestIds(threshold)) {
            try {
                aiJobService.recoverIfHeartbeatStale(requestId, threshold);
            } catch (RuntimeException exception) {
                log.warn("RUNNING AI 작업 회수 실패·건너뜀. requestId={}, exceptionType={}",
                        requestId, exception.getClass().getSimpleName());
            }
        }
    }
}
