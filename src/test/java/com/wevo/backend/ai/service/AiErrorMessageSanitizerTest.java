package com.wevo.backend.ai.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiErrorMessageSanitizerTest {

    private final AiErrorMessageSanitizer sanitizer = new AiErrorMessageSanitizer();

    @Test
    void redactsKnownSecretsAndLimitsLength() {
        String message = "Authorization: Bearer secret-token ANTHROPIC_API_KEY=sk-ant-secret "
                + "x".repeat(600);

        String sanitized = sanitizer.sanitize(message);

        assertThat(sanitized)
                .doesNotContain("secret-token", "sk-ant-secret")
                .hasSize(500);
    }
}
