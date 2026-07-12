package com.wevo.backend.ai.client;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("claude-integration")
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class ClaudeSmokeIntegrationTest {

    @Autowired
    private ClaudeGateway claudeGateway;

    @Test
    void callsClaudeAndReturnsMetadata() {
        ClaudeResponse response = claudeGateway.generate(new ClaudeRequest(
                "smoke",
                "You are a connectivity test. Follow the user's instruction exactly.",
                "Reply with only the word pong."
        ));

        assertThat(response.content()).isNotBlank();
        assertThat(response.model()).isNotBlank();
        assertThat(response.promptTokens()).isNotNegative();
        assertThat(response.completionTokens()).isPositive();
    }
}
