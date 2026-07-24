package com.wevo.backend.ai.context;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.domain.AiFeature;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiTokenBudgetEstimatorTest {

    @Test
    void countsNormalizedUtf8BytesAcrossPromptSchemaAndContext() {
        AiTokenBudgetEstimator estimator = new AiTokenBudgetEstimator(properties(30));

        AiTokenBudgetEstimate estimate = estimator.estimate(
                AiFeature.ISSUE_DETECTION,
                AiTokenBudgetInput.of("sys\r\n", "user", "{\"type\":\"object\"}", "가")
        );

        assertThat(estimate.estimatedInputTokens()).isEqualTo(28);
        assertThat(estimate.policyVersion())
                .isEqualTo(AiProperties.ModelOptions.CONSERVATIVE_CHAR_V1);
        assertThat(estimate.withinBudget()).isTrue();
    }

    @Test
    void rejectsOverBudgetInputWithoutIncludingRawInputInMessage() {
        AiTokenBudgetEstimator estimator = new AiTokenBudgetEstimator(properties(10));

        assertThatThrownBy(() -> estimator.requireWithinBudget(
                AiFeature.DRAFT_REVIEW,
                AiTokenBudgetInput.of("secret-input")
        ))
                .isInstanceOf(AiInputBudgetExceededException.class)
                .hasMessageNotContaining("secret-input")
                .satisfies(exception -> {
                    AiInputBudgetExceededException exceeded =
                            (AiInputBudgetExceededException) exception;
                    assertThat(exceeded.getEstimatedInputTokens()).isEqualTo(12);
                    assertThat(exceeded.getMaxInputTokens()).isEqualTo(10);
                });
    }

    static AiProperties properties(int maxInputTokens) {
        return new AiProperties(
                "none",
                new AiProperties.ModelOptions(
                        "test-model",
                        Duration.ofSeconds(1),
                        maxInputTokens,
                        1,
                        10_000,
                        1,
                        AiProperties.ModelOptions.CONSERVATIVE_CHAR_V1,
                        AiProperties.ModelOptions.REJECT_OVERSIZED_INPUT_V1,
                        0,
                        Duration.ZERO,
                        Duration.ZERO
                ),
                Map.of(),
                null
        );
    }
}
