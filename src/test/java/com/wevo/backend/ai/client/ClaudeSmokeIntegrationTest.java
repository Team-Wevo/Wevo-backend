package com.wevo.backend.ai.client;

import com.wevo.backend.ai.domain.AiFeature;
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
                AiFeature.DRAFT_REVIEW,
                "You are a connectivity test. Follow the user's instruction exactly.",
                "Reply with only the word pong."
        ));

        assertThat(response.content()).isNotBlank();
        assertThat(response.usageMetadata().modelId()).isNotBlank();
        assertThat(response.usageMetadata().inputTokens()).isNotNegative();
        assertThat(response.usageMetadata().outputTokens()).isPositive();
    }
}
