package com.wevo.backend.ai.client;

import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.prompt.PromptRegistry;
import com.wevo.backend.ai.prompt.PromptRenderer;
import com.wevo.backend.ai.prompt.PromptTemplateId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("claude-integration")
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class ClaudeSmokeIntegrationTest {

    @Autowired
    private ClaudeGateway claudeGateway;

    @Autowired
    private PromptRegistry promptRegistry;

    @Autowired
    private PromptRenderer promptRenderer;

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

    @Test
    void callsClaudeWithProviderNativeStructuredOutput() {
        var prompt = promptRenderer.render(
                promptRegistry.get(new PromptTemplateId("contract-summary", 1)),
                Map.of("sourceText", "Wevo turns team opinions into a shared project draft.")
        );
        StructuredOutputDefinition<SmokeStructuredOutput> definition = StructuredOutputDefinition.of(
                new OutputSchemaId("smoke-structured-output", 1),
                SmokeStructuredOutput.class
        );

        StructuredClaudeResponse<SmokeStructuredOutput> response = claudeGateway.generateStructured(
                new StructuredClaudeRequest<>(
                        AiFeature.DRAFT_REVIEW,
                        prompt,
                        definition,
                        StructuredOutputValidationContext.empty()
                )
        );

        assertThat(response.result().summary()).isNotBlank();
        assertThat(response.usageMetadata().modelId()).isNotBlank();
        assertThat(response.schemaId()).isEqualTo(new OutputSchemaId("smoke-structured-output", 1));
    }

    private record SmokeStructuredOutput(String summary) {
    }
}
