package com.wevo.backend.ai.exception;

import com.wevo.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiExceptionTranslatorTest {

    private final OpenAiExceptionTranslator translator = new OpenAiExceptionTranslator();

    @Test
    void translatesTimeoutCauseWithoutExposingProviderDetails() {
        AiProviderException translated = translator.translate(
                new IllegalStateException("provider-secret", new SocketTimeoutException("provider-secret"))
        );

        assertThat(translated.getErrorCode()).isEqualTo(ErrorCode.AI_PROVIDER_TIMEOUT);
        assertThat(translated.getMessage()).doesNotContain("provider-secret");
    }

    @Test
    void translatesConnectionCauseToUnavailable() {
        AiProviderException translated = translator.translate(
                new IllegalStateException("transport failed", new ConnectException("connection refused"))
        );

        assertThat(translated.getErrorCode()).isEqualTo(ErrorCode.AI_PROVIDER_UNAVAILABLE);
    }
}
