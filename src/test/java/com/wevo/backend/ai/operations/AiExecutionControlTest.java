package com.wevo.backend.ai.operations;

import com.wevo.backend.ai.config.AiOperationsProperties;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.exception.AiProviderException;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiExecutionControlTest {

    @Test
    void transitionsClosedOpenHalfOpenAndClosesOnSuccessfulProbe() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-02T00:00:00Z"));
        AiExecutionControl control = new AiExecutionControl(properties(true, Map.of()), clock);

        control.beforeProviderCall(AiFeature.DRAFT_GENERATION);
        control.recordFailure(providerFailure());
        control.beforeProviderCall(AiFeature.DRAFT_GENERATION);
        control.recordFailure(providerFailure());

        assertThat(control.state()).isEqualTo(AiCircuitState.OPEN);
        assertThatThrownBy(() -> control.beforeProviderCall(AiFeature.DRAFT_GENERATION))
                .isInstanceOf(AiProviderException.class);

        clock.advance(Duration.ofSeconds(31));
        assertThat(control.state()).isEqualTo(AiCircuitState.HALF_OPEN);
        control.requireSubmissionAllowed(AiFeature.DRAFT_GENERATION);
        assertThatThrownBy(() -> control.requireSubmissionAllowed(AiFeature.DRAFT_GENERATION))
                .isInstanceOf(BusinessException.class);
        control.beforeProviderCall(AiFeature.DRAFT_GENERATION);
        assertThatThrownBy(() -> control.beforeProviderCall(AiFeature.DRAFT_GENERATION))
                .isInstanceOf(AiProviderException.class);
        control.recordSuccess();

        assertThat(control.state()).isEqualTo(AiCircuitState.CLOSED);
        control.beforeProviderCall(AiFeature.DRAFT_GENERATION);
    }

    @Test
    void globalAndFeatureKillSwitchBlockOnlyAiSubmission() {
        AiExecutionControl globalOff = new AiExecutionControl(
                properties(false, Map.of()), Clock.systemUTC());
        AiExecutionControl featureOff = new AiExecutionControl(
                properties(true, Map.of("draft-generation", false)), Clock.systemUTC());

        assertThatThrownBy(() -> globalOff.requireSubmissionAllowed(AiFeature.DRAFT_GENERATION))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.AI_PROVIDER_UNAVAILABLE));
        assertThatThrownBy(() -> featureOff.requireSubmissionAllowed(AiFeature.DRAFT_GENERATION))
                .isInstanceOf(BusinessException.class);
        featureOff.requireSubmissionAllowed(AiFeature.DRAFT_REVIEW);
    }

    private AiOperationsProperties properties(boolean enabled, Map<String, Boolean> features) {
        return new AiOperationsProperties(enabled, features,
                new AiOperationsProperties.CircuitBreaker(2, Duration.ofSeconds(30), 1));
    }

    private AiProviderException providerFailure() {
        return new AiProviderException(
                ErrorCode.AI_PROVIDER_UNAVAILABLE, new IllegalStateException("synthetic"));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Seoul");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
