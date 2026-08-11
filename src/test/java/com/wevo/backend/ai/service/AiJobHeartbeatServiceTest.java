package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.wevo.backend.ai.config.AiJobDispatchProperties;
import com.wevo.backend.ai.domain.AiFeature;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class AiJobHeartbeatServiceTest {

    private static final UUID REQUEST_ID = UUID.randomUUID();

    @Mock private AiJobService aiJobService;
    @Mock private ScheduledExecutorService scheduler;
    @Mock private ScheduledFuture<?> future;

    private AiJobHeartbeatService heartbeatService;

    @BeforeEach
    void setUp() {
        AiJobDispatchProperties properties = new AiJobDispatchProperties(
                true, Duration.ofSeconds(2), 20, 8,
                Duration.ofSeconds(15), Duration.ofSeconds(60), Duration.ofSeconds(60));
        heartbeatService = new AiJobHeartbeatService(aiJobService, scheduler, properties);
    }

    @Test
    void heartbeatFailureDoesNotStopTheNextTickAndLogsRecovery() {
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        doReturn(future).when(scheduler).scheduleAtFixedRate(
                taskCaptor.capture(), eq(15_000L), eq(15_000L), eq(TimeUnit.MILLISECONDS));
        willThrow(new IllegalStateException("로그에 노출되면 안 되는 상세 메시지"))
                .willDoNothing()
                .given(aiJobService).heartbeat(REQUEST_ID);
        ListAppender<ILoggingEvent> appender = attachAppender();

        try (AiJobHeartbeatService.HeartbeatLease ignored =
                     heartbeatService.start(REQUEST_ID, AiFeature.OPINION_SYNTHESIS)) {
            taskCaptor.getValue().run();
            taskCaptor.getValue().run();
        } finally {
            detachAppender(appender);
        }

        verify(aiJobService, times(2)).heartbeat(REQUEST_ID);
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> {
                    assertThat(message).contains("heartbeat 갱신 실패");
                    assertThat(message).contains(REQUEST_ID.toString());
                    assertThat(message).contains("OPINION_SYNTHESIS");
                    assertThat(message).contains("consecutiveFailures=1");
                    assertThat(message).contains("exceptionType=IllegalStateException");
                    assertThat(message).doesNotContain("노출되면 안 되는 상세 메시지");
                })
                .anySatisfy(message -> assertThat(message)
                        .contains("heartbeat 갱신 복구")
                        .contains("previousFailures=1"));
    }

    @Test
    void registrationFailureIsPropagatedToTheHandlerBoundary() {
        given(scheduler.scheduleAtFixedRate(
                any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .willThrow(new RejectedExecutionException("scheduler shutdown"));

        assertThatThrownBy(() -> heartbeatService.start(
                REQUEST_ID, AiFeature.OPINION_SYNTHESIS))
                .isInstanceOf(RejectedExecutionException.class);
    }

    @Test
    void leaseCancelsTheHeartbeatExactlyOnce() {
        doReturn(future).when(scheduler).scheduleAtFixedRate(
                any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS));
        AiJobHeartbeatService.HeartbeatLease lease = heartbeatService.start(
                REQUEST_ID, AiFeature.OPINION_SYNTHESIS);

        lease.close();
        lease.close();

        verify(future).cancel(false);
    }

    private ListAppender<ILoggingEvent> attachAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(AiJobHeartbeatService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void detachAppender(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(AiJobHeartbeatService.class);
        logger.detachAppender(appender);
        appender.stop();
    }
}
