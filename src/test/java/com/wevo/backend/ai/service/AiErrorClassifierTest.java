package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wevo.backend.ai.domain.AiErrorType;
import com.wevo.backend.ai.exception.AiProviderUnavailableException;
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
}
