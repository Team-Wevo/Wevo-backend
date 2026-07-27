package com.wevo.backend.ai.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AiJobDispatchPropertiesTest {

    private AiJobDispatchProperties of(Integer batch, Integer maxConcurrent,
                                       Duration heartbeatInterval, Duration heartbeatTimeout) {
        return new AiJobDispatchProperties(true, Duration.ofSeconds(2), batch, maxConcurrent,
                heartbeatInterval, heartbeatTimeout, Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("모든 값이 null이면 안전한 기본값으로 바인딩된다")
    void nullValues_useDefaults() {
        AiJobDispatchProperties properties =
                new AiJobDispatchProperties(null, null, null, null, null, null, null);

        assertThat(properties.dispatchEnabled()).isTrue();
        assertThat(properties.dispatchInterval()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.dispatchBatchSize()).isEqualTo(20);
        assertThat(properties.maxConcurrentJobs()).isEqualTo(8);
        assertThat(properties.heartbeatInterval()).isEqualTo(Duration.ofSeconds(15));
        assertThat(properties.heartbeatTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.recoveryInterval()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("정상 값은 통과한다")
    void validValues_pass() {
        assertThat(of(10, 4, Duration.ofSeconds(10), Duration.ofSeconds(40)).maxConcurrentJobs())
                .isEqualTo(4);
    }

    @Test
    @DisplayName("heartbeat-interval이 timeout 이상이면 거부한다 (첫 heartbeat 전 회수 방지)")
    void heartbeatIntervalNotLessThanTimeout_rejected() {
        assertThatThrownBy(() -> of(10, 4, Duration.ofSeconds(60), Duration.ofSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> of(10, 4, Duration.ofSeconds(90), Duration.ofSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("0 이하 또는 음수 duration은 거부한다")
    void nonPositiveDuration_rejected() {
        assertThatThrownBy(() -> of(10, 4, Duration.ZERO, Duration.ofSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> of(10, 4, Duration.ofSeconds(-10), Duration.ofSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("0 이하 batch·concurrency는 거부한다")
    void nonPositiveCounts_rejected() {
        assertThatThrownBy(() -> of(0, 4, Duration.ofSeconds(15), Duration.ofSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> of(20, 0, Duration.ofSeconds(15), Duration.ofSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
