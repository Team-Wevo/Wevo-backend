package com.wevo.backend.ai.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiErrorMessageSanitizerTest {

    private final AiErrorMessageSanitizer sanitizer = new AiErrorMessageSanitizer();

    @Test
    void redactsKnownSecretsAndLimitsLength() {
        String message = "Authorization: Bearer secret-token ANTHROPIC_API_KEY=sk-ant-secret "
                + "NVIDIA_API_KEY=" + "nvapi" + "-secret "
                + "OPENAI_API_KEY=sk-proj-verysecret user@example.com "
                + "x".repeat(600);

        String sanitized = sanitizer.sanitize(message);

        assertThat(sanitized)
                .doesNotContain("secret-token", "sk-ant-secret", "nvapi" + "-secret",
                        "sk-proj-verysecret", "user@example.com")
                .hasSize(500);
    }
}
