package com.wevo.backend.ai.context;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.domain.AiFeature;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * {@code conservative-char-v1}: LF로 정규화한 최종 입력의 UTF-8 byte 수를 token 상한으로 본다.
 */
@Component
public class AiTokenBudgetEstimator {

    private final AiProperties properties;

    public AiTokenBudgetEstimator(AiProperties properties) {
        this.properties = properties;
    }

    public AiTokenBudgetEstimate estimate(AiFeature feature, AiTokenBudgetInput input) {
        if (feature == null || input == null) {
            throw new IllegalArgumentException("AI feature와 token 계산 입력은 필수입니다.");
        }
        AiProperties.ModelOptions options = properties.optionsFor(feature);
        if (!AiProperties.ModelOptions.CONSERVATIVE_CHAR_V1.equals(options.tokenEstimationPolicy())) {
            throw new IllegalStateException("지원하지 않는 AI token 추정 정책입니다.");
        }
        long estimated = 0;
        try {
            for (String segment : input.segments()) {
                String normalized = segment.replace("\r\n", "\n").replace('\r', '\n');
                estimated = Math.addExact(
                        estimated,
                        normalized.getBytes(StandardCharsets.UTF_8).length
                );
            }
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("AI 입력 token 크기를 계산할 수 없습니다.", exception);
        }
        return new AiTokenBudgetEstimate(
                feature,
                options.tokenEstimationPolicy(),
                estimated,
                options.maxInputTokens(),
                estimated <= options.maxInputTokens()
        );
    }

    public AiTokenBudgetEstimate requireWithinBudget(
            AiFeature feature,
            AiTokenBudgetInput input
    ) {
        AiTokenBudgetEstimate estimate = estimate(feature, input);
        if (!estimate.withinBudget()) {
            throw new AiInputBudgetExceededException(
                    feature,
                    estimate.estimatedInputTokens(),
                    estimate.maxInputTokens()
            );
        }
        return estimate;
    }
}
