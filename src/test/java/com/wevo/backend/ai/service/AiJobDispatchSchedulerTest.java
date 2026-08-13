package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.wevo.backend.ai.config.AiJobDispatchProperties;
import com.wevo.backend.ai.domain.AiFeature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiJobDispatchSchedulerTest {

    @Mock private AiJobService aiJobService;
    @Mock private AiJobExecutor aiJobExecutor;
    @Mock private ReviewIntentComparisonRecoveryService comparisonRecoveryService;

    private AiJobDispatchScheduler scheduler;

    @BeforeEach
    void setUp() {
        AiJobDispatchProperties properties = new AiJobDispatchProperties(true, Duration.ofSeconds(2), 20, 8,
                Duration.ofSeconds(15), Duration.ofSeconds(60), Duration.ofSeconds(60));
        Clock clock = Clock.fixed(Instant.parse("2026-07-24T05:00:00Z"), ZoneOffset.UTC);
        scheduler = new AiJobDispatchScheduler(
                aiJobService, aiJobExecutor, comparisonRecoveryService, properties, clock);
    }

    @Test
    @DisplayName("실행 가능한 기능의 큐를 가용 슬롯 수만큼만 뽑아 실행에 넘긴다")
    void dispatchQueued_fetchesUpToAvailableSlots() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        given(aiJobExecutor.supportedFeatures()).willReturn(Set.of(AiFeature.OPINION_SYNTHESIS));
        given(aiJobExecutor.availableSlots()).willReturn(2); // batchSize=20보다 작음
        given(aiJobService.findNextQueuedRequestIds(anyCollection(), eq(2))).willReturn(List.of(a, b));

        scheduler.dispatchQueued();

        verify(aiJobService).findNextQueuedRequestIds(anyCollection(), eq(2));
        verify(aiJobExecutor).dispatch(a);
        verify(aiJobExecutor).dispatch(b);
    }

    @Test
    @DisplayName("가용 슬롯이 없으면 큐를 조회하지도 않는다")
    void dispatchQueued_noSlots_doesNothing() {
        given(aiJobExecutor.supportedFeatures()).willReturn(Set.of(AiFeature.OPINION_SYNTHESIS));
        given(aiJobExecutor.availableSlots()).willReturn(0);

        scheduler.dispatchQueued();

        verify(aiJobService, never()).findNextQueuedRequestIds(anyCollection(), anyInt());
        verify(aiJobExecutor, never()).dispatch(any());
    }

    @Test
    @DisplayName("등록된 핸들러가 없으면 큐를 조회하지 않는다")
    void dispatchQueued_noHandlers_doesNothing() {
        given(aiJobExecutor.supportedFeatures()).willReturn(Set.of());

        scheduler.dispatchQueued();

        verify(aiJobService, never()).findNextQueuedRequestIds(anyCollection(), anyInt());
    }

    @Test
    @DisplayName("heartbeat가 끊긴 RUNNING 작업을 조건 재확인 후 회수한다")
    void recoverStalledRunning_recoversWithRecheck() {
        UUID stalled = UUID.randomUUID();
        given(aiJobService.findHeartbeatTimedOutRequestIds(any())).willReturn(List.of(stalled));

        scheduler.recoverStalledRunning();

        verify(aiJobService).recoverIfHeartbeatStale(eq(stalled), any());
        verify(aiJobService).findApplicationTimedOutRequestIds(any());
        verify(comparisonRecoveryService).recoverPendingComparisons(any(), eq(20));
    }

    @Test
    @DisplayName("heartbeat가 살아 있어도 application timeout을 넘긴 RUNNING 작업을 회수한다")
    void recoverStalledRunning_recoversApplicationTimedOutJob() {
        UUID timedOut = UUID.randomUUID();
        given(aiJobService.findHeartbeatTimedOutRequestIds(any())).willReturn(List.of());
        given(aiJobService.findApplicationTimedOutRequestIds(any())).willReturn(List.of(timedOut));
        given(aiJobService.recoverIfApplicationTimedOut(eq(timedOut), any())).willReturn(true);

        scheduler.recoverStalledRunning();

        verify(aiJobService).recoverIfApplicationTimedOut(eq(timedOut), any());
    }

    @Test
    @DisplayName("heartbeat timeout 회수 성공 시 requestId와 판단 기준을 기록한다")
    void recoverStalledRunning_logsSuccessfulRecovery() {
        UUID stalled = UUID.randomUUID();
        given(aiJobService.findHeartbeatTimedOutRequestIds(any())).willReturn(List.of(stalled));
        given(aiJobService.recoverIfHeartbeatStale(eq(stalled), any())).willReturn(true);
        Logger logger = (Logger) LoggerFactory.getLogger(AiJobDispatchScheduler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            scheduler.recoverStalledRunning();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message)
                        .contains("heartbeat timeout 회수")
                        .contains(stalled.toString())
                        .contains("heartbeatTimeoutMs=60000"));
    }
}
