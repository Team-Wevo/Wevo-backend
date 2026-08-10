package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.exception.AiProviderUnavailableException;
import com.wevo.backend.global.exception.ErrorCode;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiExecutionAvailabilityGuardTest {

    @Test
    void explicitNoneProviderRejectsNewExecutionWithAI008() {
        AiExecutionAvailabilityGuard guard =
                new AiExecutionAvailabilityGuard(properties("none"));

        assertThatThrownBy(guard::requireAvailable)
                .isInstanceOf(AiProviderUnavailableException.class)
                .extracting(error -> ((AiProviderUnavailableException) error).getErrorCode())
                .isEqualTo(ErrorCode.AI_PROVIDER_UNAVAILABLE);
    }

    @Test
    void configuredProviderAllowsNewExecution() {
        AiExecutionAvailabilityGuard guard =
                new AiExecutionAvailabilityGuard(properties("openai"));

        assertThatCode(guard::requireAvailable).doesNotThrowAnyException();
    }

    private AiProperties properties(String provider) {
        return new AiProperties(
                provider,
                new AiProperties.ModelOptions(
                        "model",
                        Duration.ofSeconds(1),
                        1_000,
                        128,
                        2_048,
                        256,
                        AiProperties.ModelOptions.CONSERVATIVE_CHAR_V1,
                        AiProperties.ModelOptions.REJECT_OVERSIZED_INPUT_V1,
                        0,
                        Duration.ZERO,
                        Duration.ZERO
                ),
                Map.of(),
                null,
                "openai".equals(provider)
                        ? new AiProperties.OpenAiOptions(
                                "test-api-key", null, "model", Duration.ofSeconds(1),
                                128, "medium", 2_048)
                        : null
        );
    }
}
