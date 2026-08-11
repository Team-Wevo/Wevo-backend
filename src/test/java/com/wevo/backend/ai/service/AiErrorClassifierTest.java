package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wevo.backend.ai.domain.AiErrorType;
import com.wevo.backend.ai.context.AiInputBudgetExceededException;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.exception.AiProviderUnavailableException;
import com.wevo.backend.ai.prompt.PromptException;
import com.wevo.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class AiErrorClassifierTest {

    private final AiErrorClassifier classifier = new AiErrorClassifier();

    @Test
    void providerNotConfiguredMapsToUnavailableInsteadOfInternalError() {
        AiProviderUnavailableException exception = new AiProviderUnavailableException();

        assertThat(classifier.classify(exception)).isEqualTo(AiErrorType.PROVIDER_UNAVAILABLE);
        assertThat(classifier.classify(exception).toErrorCode())
                .isEqualTo(ErrorCode.AI_PROVIDER_UNAVAILABLE);
        assertThat(classifier.safeMessage(exception))
                .isEqualTo(ErrorCode.AI_PROVIDER_UNAVAILABLE.getMessage());
    }

    @Test
    void deterministicInputBudgetFailuresUseActionableExternalCode() {
        AiInputBudgetExceededException budgetExceeded =
                new AiInputBudgetExceededException(AiFeature.OPINION_SYNTHESIS, 101, 100);
        PromptException promptTooLarge =
                new PromptException(ErrorCode.AI_INPUT_BUDGET_EXCEEDED);

        assertThat(classifier.classify(budgetExceeded))
                .isEqualTo(AiErrorType.INPUT_BUDGET_EXCEEDED);
        assertThat(classifier.classify(promptTooLarge))
                .isEqualTo(AiErrorType.INPUT_BUDGET_EXCEEDED);
        assertThat(classifier.classify(budgetExceeded).toErrorCode())
                .isEqualTo(ErrorCode.AI_INPUT_BUDGET_EXCEEDED);
        assertThat(classifier.safeMessage(budgetExceeded))
                .isEqualTo(ErrorCode.AI_INPUT_BUDGET_EXCEEDED.getMessage());
        assertThat(classifier.safeMessage(promptTooLarge))
                .isEqualTo(ErrorCode.AI_INPUT_BUDGET_EXCEEDED.getMessage());
    }
}
