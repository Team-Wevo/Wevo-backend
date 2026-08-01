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

@Tag("nvidia-integration")
@SpringBootTest(properties = {
        "wevo.ai.nvidia.api-key=${NVIDIA_API_KEY}",
        "wevo.ai.nvidia.base-url=${NVIDIA_API_BASE_URL:https://integrate.api.nvidia.com}",
        "wevo.ai.default-options.model=${NVIDIA_API_MODEL:mistralai/mistral-medium-3.5-128b}",
        "wevo.ai.default-options.timeout=${NVIDIA_API_TIMEOUT:60s}",
        "wevo.ai.default-options.max-output-tokens=128",
        "wevo.ai.nvidia.temperature=${NVIDIA_API_TEMPERATURE:0.1}",
        "wevo.ai.nvidia.reasoning-effort=${NVIDIA_API_REASONING_EFFORT:none}"
})
@EnabledIfEnvironmentVariable(named = "NVIDIA_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "NVIDIA_INTEGRATION_ENABLED", matches = "(?i)true")
class NvidiaSmokeIntegrationTest {

    @Autowired
    private AiProviderGateway providerGateway;

    @Autowired
    private PromptRegistry promptRegistry;

    @Autowired
    private PromptRenderer promptRenderer;

    @Test
    void callsNvidiaWithSyntheticTextAndReturnsMetadata() {
        AiProviderResponse response = providerGateway.generate(new AiProviderRequest(
                AiFeature.DRAFT_REVIEW,
                "You are a connectivity test. Follow the user's instruction exactly.",
                "Reply with only the word pong. This is synthetic test data."
        ));

        assertThat(response.content()).isNotBlank();
        assertThat(response.usageMetadata().providerId()).isEqualTo("nvidia");
        assertThat(response.usageMetadata().modelId()).isNotBlank();
        if (response.usageMetadata().inputTokens() != null) {
            assertThat(response.usageMetadata().inputTokens()).isNotNegative();
        }
        if (response.usageMetadata().outputTokens() != null) {
            assertThat(response.usageMetadata().outputTokens()).isPositive();
        }
    }

    @Test
    void validatesNvidiaStructuredOutputWithCommonSchemaPipeline() {
        var prompt = promptRenderer.render(
                promptRegistry.get(new PromptTemplateId("contract-summary", 1)),
                Map.of("sourceText", "Synthetic: Wevo turns team opinions into a shared project draft.")
        );
        StructuredOutputDefinition<SmokeStructuredOutput> definition = StructuredOutputDefinition.of(
                new OutputSchemaId("smoke-structured-output", 1),
                SmokeStructuredOutput.class
        );

        StructuredAiProviderResponse<SmokeStructuredOutput> response = providerGateway.generateStructured(
                new StructuredAiProviderRequest<>(
                        AiFeature.DRAFT_REVIEW,
                        prompt,
                        definition,
                        StructuredOutputValidationContext.empty()
                )
        );

        assertThat(response.result().summary()).isNotBlank();
        assertThat(response.usageMetadata().providerId()).isEqualTo("nvidia");
        assertThat(response.schemaId()).isEqualTo(new OutputSchemaId("smoke-structured-output", 1));
    }

    private record SmokeStructuredOutput(String summary) {
    }
}
