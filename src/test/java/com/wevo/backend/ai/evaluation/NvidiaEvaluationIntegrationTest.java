package com.wevo.backend.ai.evaluation;

import com.wevo.backend.ai.client.AiProviderGateway;
import com.wevo.backend.ai.client.OutputSchemaId;
import com.wevo.backend.ai.client.StructuredAiProviderRequest;
import com.wevo.backend.ai.client.StructuredOutputDefinition;
import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.config.NvidiaProviderProperties;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.prompt.PromptRegistry;
import com.wevo.backend.ai.prompt.PromptRenderer;
import com.wevo.backend.ai.prompt.PromptTemplateId;
import com.wevo.backend.ai.service.AiCostCalculator;
import com.wevo.backend.ai.service.AiErrorClassifier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("nvidia-evaluation")
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
@EnabledIfEnvironmentVariable(named = "NVIDIA_EVALUATION_ENABLED", matches = "(?i)true")
class NvidiaEvaluationIntegrationTest {

    @Autowired
    private AiProviderGateway providerGateway;

    @Autowired
    private PromptRegistry promptRegistry;

    @Autowired
    private PromptRenderer promptRenderer;

    @Autowired
    private AiCostCalculator costCalculator;

    @Autowired
    private AiErrorClassifier errorClassifier;

    @Autowired
    private NvidiaProviderProperties nvidiaProviderProperties;

    @Test
    void evaluatesOnlySyntheticFixtureWithinBudgetsAndWritesSanitizedReport() {
        AiEvaluationFixture fixture = new AiEvaluationFixtureLoader().load(
                "ai/evaluation/issue-detection/all-agreed-proposal.json"
        );
        List<AiEvaluationFixture> fixtures = List.of(fixture);
        new AiEvaluationLiveGuard(System::getenv).assertAllowed(fixtures);
        LiveAiFixtureEvaluator<SummaryOutput> evaluator = new LiveAiFixtureEvaluator<>(
                providerGateway,
                evaluationCase(),
                costCalculator,
                errorClassifier
        );
        AiEvaluationBudget budget = new AiEvaluationBudget(
                1,
                3,
                256,
                Duration.ofSeconds(60),
                null
        );

        List<AiEvaluationSample> samples = new AiEvaluationRunner().run(fixtures, evaluator, budget);

        assertThat(samples).singleElement().satisfies(sample -> {
            assertThat(sample.outcome()).isEqualTo(AiEvaluationOutcome.SUCCESS);
            assertThat(sample.attemptCount()).isBetween(1, 3);
        });
        AiEvaluationReport report = new AiEvaluationReportFactory().create(
                new AiEvaluationRunMetadata(
                        "issue-detection-v1",
                        "nvidia",
                        samples.getFirst().usage().modelId(),
                        "contract-summary:v1",
                        "evaluation-summary:v1",
                        Instant.now(),
                        nvidiaProviderProperties.temperature(),
                        128,
                        environmentOrDefault("GIT_COMMIT", "unknown")
                ),
                fixtures,
                samples,
                null,
                AiEvaluationGate.DEFAULT_MINIMUM_SAMPLES
        );
        AiEvaluationReportWriter.WrittenReport written = new AiEvaluationReportWriter().write(
                report,
                Path.of("build/reports/ai-evaluation/nvidia-live"),
                "issue-detection-v1-current"
        );
        assertThat(written.jsonPath()).exists();
    }

    private LiveEvaluationCase<SummaryOutput> evaluationCase() {
        return new LiveEvaluationCase<>() {
            @Override
            public StructuredAiProviderRequest<SummaryOutput> requestFor(AiEvaluationFixture fixture) {
                String source = fixture.input().opinions().stream()
                        .filter(AiEvaluationFixture.Opinion::submitted)
                        .filter(opinion -> !opinion.deleted())
                        .map(opinion -> opinion.id() + ": " + opinion.content())
                        .collect(Collectors.joining("\n"));
                return new StructuredAiProviderRequest<>(
                        AiFeature.ISSUE_DETECTION,
                        promptRenderer.render(
                                promptRegistry.get(new PromptTemplateId("contract-summary", 1)),
                                Map.of("sourceText", source)
                        ),
                        StructuredOutputDefinition.of(
                                new OutputSchemaId("evaluation-summary", 1),
                                SummaryOutput.class
                        ),
                        StructuredOutputValidationContext.empty()
                );
            }

            @Override
            public AiEvaluationCandidate normalize(SummaryOutput result) {
                return new AiEvaluationCandidate(
                        List.of(),
                        List.of(new AiEvaluationCandidate.Claim(result.summary(), false, Set.of())),
                        Set.of(),
                        0,
                        0
                );
            }
        };
    }

    private String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private record SummaryOutput(String summary) {
    }
}
