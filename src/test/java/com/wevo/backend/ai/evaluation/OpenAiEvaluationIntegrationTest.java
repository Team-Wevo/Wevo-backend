package com.wevo.backend.ai.evaluation;

import com.wevo.backend.ai.client.AiProviderGateway;
import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.config.AiPricingProperties;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.prompt.DraftGenerationPromptFactory;
import com.wevo.backend.ai.prompt.DraftReviewPromptFactory;
import com.wevo.backend.ai.prompt.IssueDetectionPromptFactory;
import com.wevo.backend.ai.prompt.SynthesisPromptFactory;
import com.wevo.backend.ai.service.AiCostCalculator;
import com.wevo.backend.ai.service.AiErrorClassifier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ai.util.JacksonUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("openai-evaluation")
@SpringBootTest(properties = {
        "wevo.ai.provider=openai",
        "wevo.ai.openai.api-key=${OPENAI_API_KEY}",
        "wevo.ai.openai.base-url=${OPENAI_API_BASE_URL:https://api.openai.com}",
        "wevo.ai.openai.model=${OPENAI_API_MODEL:gpt-5.6-luna}",
        "wevo.ai.openai.timeout=${OPENAI_API_TIMEOUT:60s}",
        "wevo.ai.openai.max-output-tokens=${OPENAI_API_MAX_TOKENS:4096}",
        "wevo.ai.openai.reasoning-effort=${OPENAI_API_REASONING_EFFORT:medium}",
        "wevo.ai.default-options.max-input-tokens=${AI_DEFAULT_MAX_INPUT_TOKENS:100000}",
        "wevo.ai.default-options.model-context-limit=${AI_MODEL_CONTEXT_LIMIT:131072}",
        "wevo.ai.default-options.safety-margin-tokens=${AI_SAFETY_MARGIN_TOKENS:8192}",
        "wevo.ai.default-options.max-retries=${AI_MAX_RETRIES:2}",
        "wevo.ai.default-options.initial-backoff=500ms",
        "wevo.ai.default-options.max-backoff=8s",
        "wevo.ai.features.issue-detection.max-input-tokens=${AI_ISSUE_DETECTION_MAX_INPUT_TOKENS:64000}",
        "wevo.ai.features.opinion-synthesis.max-input-tokens=${AI_OPINION_SYNTHESIS_MAX_INPUT_TOKENS:72000}",
        "wevo.ai.features.draft-generation.max-input-tokens=${AI_DRAFT_GENERATION_MAX_INPUT_TOKENS:48000}",
        "wevo.ai.features.draft-review.max-input-tokens=${AI_DRAFT_REVIEW_MAX_INPUT_TOKENS:64000}"
})
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "OPENAI_EVALUATION_ENABLED", matches = "(?i)true")
class OpenAiEvaluationIntegrationTest {

    private static final Map<AiFeature, String> DATASETS = Map.of(
            AiFeature.ISSUE_DETECTION,
            "ai/evaluation/issue-detection/dataset-v1.index.json",
            AiFeature.OPINION_SYNTHESIS,
            "ai/evaluation/opinion-synthesis/dataset-v1.index.json",
            AiFeature.DRAFT_GENERATION,
            "ai/evaluation/draft-generation/dataset-v1.index.json",
            AiFeature.DRAFT_REVIEW,
            "ai/evaluation/draft-review/dataset-v1.index.json"
    );

    @Autowired
    private AiProviderGateway providerGateway;

    @Autowired
    private AiCostCalculator costCalculator;

    @Autowired
    private AiErrorClassifier errorClassifier;

    @Autowired
    private AiProperties aiProperties;

    @Autowired
    private AiPricingProperties aiPricingProperties;

    @Autowired
    private IssueDetectionPromptFactory issueDetectionPromptFactory;

    @Autowired
    private SynthesisPromptFactory synthesisPromptFactory;

    @Autowired
    private DraftGenerationPromptFactory draftGenerationPromptFactory;

    @Autowired
    private DraftReviewPromptFactory draftReviewPromptFactory;

    @Test
    void evaluatesAllProductContractsWithinOneGlobalBudgetAndWritesSanitizedReports()
            throws Exception {
        AiEvaluationFixtureLoader loader = new AiEvaluationFixtureLoader();
        Map<AiFeature, List<AiEvaluationFixture>> fixturesByFeature = new EnumMap<>(AiFeature.class);
        DATASETS.forEach((feature, path) -> fixturesByFeature.put(feature, loader.loadDataset(path)));
        List<AiEvaluationFixture> fixtures = fixturesByFeature.values().stream()
                .flatMap(List::stream)
                .toList();
        new AiEvaluationLiveGuard(System::getenv).assertAllowed("openai", fixtures);

        ProductLiveEvaluationCases cases = new ProductLiveEvaluationCases(
                issueDetectionPromptFactory,
                synthesisPromptFactory,
                draftGenerationPromptFactory,
                draftReviewPromptFactory
        );
        AiEvaluationBudget budget = budget();
        List<AiEvaluationSample> samples = new AiEvaluationRunner().run(
                fixtures,
                fixture -> evaluate(cases, fixture),
                budget
        );

        assertThat(samples).hasSameSizeAs(fixtures);
        assertThat(samples.stream().filter(AiEvaluationSample::providerCallStarted).count())
                .isLessThanOrEqualTo(budget.maxFixtures());
        assertThat(samples.stream().mapToInt(AiEvaluationSample::attemptCount).sum())
                .isLessThanOrEqualTo(budget.maxProviderRequests());

        String effort = aiProperties.openai().reasoningEffort();
        Path reportDirectory = Path.of("build/reports/ai-evaluation/openai", effort);
        AiEvaluationReportWriter writer = new AiEvaluationReportWriter();
        for (Map.Entry<AiFeature, List<AiEvaluationFixture>> entry : fixturesByFeature.entrySet()) {
            List<AiEvaluationFixture> featureFixtures = entry.getValue();
            Set<String> fixtureIds = featureFixtures.stream()
                    .map(fixture -> fixture.metadata().id())
                    .collect(Collectors.toUnmodifiableSet());
            List<AiEvaluationSample> featureSamples = samples.stream()
                    .filter(sample -> fixtureIds.contains(sample.fixtureId()))
                    .toList();
            AiEvaluationReport report = createReport(
                    entry.getKey(), effort, featureFixtures, featureSamples,
                    compatibleMediumBaseline(entry.getKey(), effort)
            );
            String stem = entry.getKey().configKey() + "-" + effort;
            AiEvaluationReportWriter.WrittenReport written = writer.write(
                    report, reportDirectory, stem
            );
            assertThat(written.jsonPath()).exists();
            String reportText = Files.readString(written.jsonPath())
                    + Files.readString(written.markdownPath());
            assertThat(reportText).doesNotContain(
                    "Authorization: Bearer", aiProperties.openai().apiKey()
            );
            featureFixtures.forEach(fixture -> {
                assertThat(reportText).doesNotContain(fixture.input().projectContext());
                fixture.input().opinions().forEach(opinion ->
                        assertThat(reportText).doesNotContain(opinion.content()));
            });
        }
    }

    private AiEvaluationObservation evaluate(
            ProductLiveEvaluationCases cases,
            AiEvaluationFixture fixture
    ) {
        return switch (fixture.metadata().feature()) {
            case ISSUE_DETECTION -> evaluator(cases.issueDetection()).evaluate(fixture);
            case OPINION_SYNTHESIS -> evaluator(cases.opinionSynthesis()).evaluate(fixture);
            case DRAFT_GENERATION -> evaluator(cases.draftGeneration()).evaluate(fixture);
            case DRAFT_REVIEW -> evaluator(cases.draftReview()).evaluate(fixture);
            default -> throw new IllegalArgumentException("평가 대상이 아닌 AI 기능입니다.");
        };
    }

    private <T> LiveAiFixtureEvaluator<T> evaluator(LiveEvaluationCase<T> evaluationCase) {
        return new LiveAiFixtureEvaluator<>(
                providerGateway, evaluationCase, costCalculator, errorClassifier
        );
    }

    private AiEvaluationReport createReport(
            AiFeature feature,
            String effort,
            List<AiEvaluationFixture> fixtures,
            List<AiEvaluationSample> samples,
            AiEvaluationReport baseline
    ) {
        Set<String> actualModels = samples.stream()
                .map(AiEvaluationSample::usage)
                .filter(java.util.Objects::nonNull)
                .map(usage -> usage.modelId())
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
        if (actualModels.size() > 1) {
            throw new IllegalStateException("한 feature 실행에서 실제 응답 model이 변경되었습니다.");
        }
        String actualModel = actualModels.stream().findFirst().orElse("not-observed");
        AiEvaluationRunMetadata metadata = new AiEvaluationRunMetadata(
                fixtures.getFirst().metadata().datasetVersion(),
                "openai",
                actualModel,
                "chat-completions",
                effort,
                promptVersion(feature),
                schemaVersion(feature),
                Instant.now(),
                null,
                aiProperties.optionsFor(feature).maxOutputTokens(),
                environmentOrDefault("GIT_COMMIT", "unknown")
        );
        return new AiEvaluationReportFactory().create(
                metadata,
                fixtures,
                samples,
                baseline,
                AiEvaluationGate.DEFAULT_MINIMUM_SAMPLES
        );
    }

    private AiEvaluationReport compatibleMediumBaseline(AiFeature feature, String effort) {
        if (!"low".equals(effort)) {
            return null;
        }
        Path baselinePath = Path.of(
                "build/reports/ai-evaluation/openai/medium",
                feature.configKey() + "-medium.json"
        );
        if (!Files.isRegularFile(baselinePath)) {
            return null;
        }
        try {
            return JacksonUtils.getDefaultJsonMapper().readValue(
                    baselinePath.toFile(), AiEvaluationReport.class
            );
        } catch (Exception exception) {
            throw new IllegalStateException("medium baseline report를 읽을 수 없습니다.", exception);
        }
    }

    private String promptVersion(AiFeature feature) {
        return switch (feature) {
            case ISSUE_DETECTION -> IssueDetectionPromptFactory.PROMPT_ID.trackingValue();
            case OPINION_SYNTHESIS -> SynthesisPromptFactory.PROMPT_ID.trackingValue();
            case DRAFT_GENERATION -> DraftGenerationPromptFactory.PROMPT_ID.trackingValue();
            case DRAFT_REVIEW -> DraftReviewPromptFactory.PROMPT_ID.trackingValue();
            default -> throw new IllegalArgumentException("평가 대상이 아닌 AI 기능입니다.");
        };
    }

    private String schemaVersion(AiFeature feature) {
        return switch (feature) {
            case ISSUE_DETECTION -> com.wevo.backend.ai.service.IssueDetectionOutputDefinition
                    .SCHEMA_ID.trackingValue();
            case OPINION_SYNTHESIS -> com.wevo.backend.ai.service.SynthesisOutputDefinition
                    .SCHEMA_ID.trackingValue();
            case DRAFT_GENERATION -> com.wevo.backend.ai.service.DraftGenerationOutputDefinition
                    .SCHEMA_ID.trackingValue();
            case DRAFT_REVIEW -> com.wevo.backend.ai.service.DraftReviewOutputDefinition
                    .SCHEMA_ID.trackingValue();
            default -> throw new IllegalArgumentException("평가 대상이 아닌 AI 기능입니다.");
        };
    }

    private AiEvaluationBudget budget() {
        return new AiEvaluationBudget(
                integerEnvironment("OPENAI_EVALUATION_MAX_FIXTURES", 40),
                integerEnvironment("OPENAI_EVALUATION_MAX_PROVIDER_REQUESTS", 360),
                maximumProviderRequestsPerFixture(),
                longEnvironment("OPENAI_EVALUATION_MAX_OUTPUT_TOKENS", 120_000L),
                maximumOutputTokensPerFixture(),
                durationEnvironment("OPENAI_EVALUATION_DEADLINE", Duration.ofMinutes(20)),
                decimalEnvironment("OPENAI_EVALUATION_MAX_COST_USD", new BigDecimal("5.00")),
                maximumEstimatedCostPerFixture()
        );
    }

    private int maximumProviderRequestsPerFixture() {
        int transportAttempts = DATASETS.keySet().stream()
                .mapToInt(feature -> aiProperties.optionsFor(feature).maxRetries() + 1)
                .max()
                .orElseThrow();
        int correctionAttempts = aiProperties.structuredOutput().maxCorrectionRetries() + 1;
        return Math.multiplyExact(transportAttempts, correctionAttempts);
    }

    private long maximumOutputTokensPerFixture() {
        int maximumFeatureOutput = DATASETS.keySet().stream()
                .mapToInt(feature -> aiProperties.optionsFor(feature).maxOutputTokens())
                .max()
                .orElseThrow();
        return Math.multiplyExact((long) maximumFeatureOutput, maximumProviderRequestsPerFixture());
    }

    private BigDecimal maximumEstimatedCostPerFixture() {
        AiPricingProperties.ModelPricing pricing = aiPricingProperties.models()
                .get(aiProperties.openai().model());
        if (pricing == null) {
            throw new IllegalStateException("OpenAI evaluation model의 가격 설정이 필요합니다.");
        }
        long maximumInput = DATASETS.keySet().stream()
                .mapToLong(feature -> aiProperties.optionsFor(feature).maxInputTokens())
                .max()
                .orElseThrow();
        BigDecimal maximumInputRate = pricing.inputPerMillionTokens()
                .max(pricing.cacheWritePerMillionTokens());
        BigDecimal inputCost = BigDecimal.valueOf(maximumInput)
                .multiply(BigDecimal.valueOf(maximumProviderRequestsPerFixture()))
                .multiply(maximumInputRate);
        BigDecimal outputCost = BigDecimal.valueOf(maximumOutputTokensPerFixture())
                .multiply(pricing.outputPerMillionTokens());
        return inputCost.add(outputCost)
                .divide(BigDecimal.valueOf(1_000_000L), 12, RoundingMode.UP);
    }

    private int integerEnvironment(String name, int defaultValue) {
        return Math.toIntExact(longEnvironment(name, defaultValue));
    }

    private long longEnvironment(String name, long defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : Long.parseLong(value);
    }

    private BigDecimal decimalEnvironment(String name, BigDecimal defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : new BigDecimal(value);
    }

    private Duration durationEnvironment(String name, Duration defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (normalized.endsWith("ms")) {
            return Duration.ofMillis(Long.parseLong(normalized.substring(0, normalized.length() - 2)));
        }
        if (normalized.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        if (normalized.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        return Duration.parse(value);
    }

    private String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
