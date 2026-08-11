package com.wevo.backend.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AiHeartbeatSchedulerConfigTest {

    @Test
    void schedulerCapacityAndShutdownPoliciesFollowJobConcurrency() throws Exception {
        AiJobDispatchProperties properties = new AiJobDispatchProperties(
                true, Duration.ofSeconds(2), 20, 3,
                Duration.ofSeconds(15), Duration.ofSeconds(60), Duration.ofSeconds(60));
        ScheduledThreadPoolExecutor executor = (ScheduledThreadPoolExecutor)
                new AiConfig().aiHeartbeatScheduler(properties);

        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(3);
            assertThat(executor.getRemoveOnCancelPolicy()).isTrue();
            assertThat(executor.getContinueExistingPeriodicTasksAfterShutdownPolicy()).isFalse();
            assertThat(executor.getExecuteExistingDelayedTasksAfterShutdownPolicy()).isFalse();
            assertThat(executor.submit(() -> Thread.currentThread().getName()).get(1, TimeUnit.SECONDS))
                    .startsWith("ai-heartbeat-");

            CountDownLatch blockersStarted = new CountDownLatch(2);
            CountDownLatch releaseBlockers = new CountDownLatch(1);
            CountDownLatch thirdHeartbeatRan = new CountDownLatch(1);
            Runnable blockingHeartbeat = () -> {
                blockersStarted.countDown();
                try {
                    releaseBlockers.await(1, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            };
            executor.execute(blockingHeartbeat);
            executor.execute(blockingHeartbeat);
            assertThat(blockersStarted.await(1, TimeUnit.SECONDS)).isTrue();
            executor.execute(thirdHeartbeatRan::countDown);
            assertThat(thirdHeartbeatRan.await(1, TimeUnit.SECONDS)).isTrue();
            releaseBlockers.countDown();

            ScheduledFuture<?> delayed = executor.schedule(() -> { }, 1, TimeUnit.HOURS);
            delayed.cancel(false);
            assertThat(executor.getQueue()).isEmpty();
        } finally {
            executor.shutdownNow();
        }
    }
}
