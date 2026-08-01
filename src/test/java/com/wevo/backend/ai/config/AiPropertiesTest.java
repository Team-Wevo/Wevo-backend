package com.wevo.backend.ai.config;

import com.wevo.backend.ai.domain.AiFeature;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiPropertiesTest {

    @Test
    void featureOptionsOverrideOnlyConfiguredValues() {
        AiProperties properties = new AiProperties(
                "nvidia",
                modelOptions("default-model", Duration.ofSeconds(60), 4096),
                Map.of("draft-generation", new AiProperties.FeatureOptions(
                        "draft-model", null, null, 2048, null, null,
                        null, null, null, null, null
                )),
                null
        );

        AiProperties.ModelOptions options = properties.optionsFor(AiFeature.DRAFT_GENERATION);

        assertThat(options.model()).isEqualTo("draft-model");
        assertThat(options.timeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(options.maxOutputTokens()).isEqualTo(2048);
        assertThat(options.maxInputTokens()).isEqualTo(100_000);
        assertThat(options.modelContextLimit()).isEqualTo(131_072);
    }

    @Test
    void unknownFeatureUsesDefaultOptions() {
        AiProperties.ModelOptions defaultOptions =
                modelOptions("default-model", Duration.ofSeconds(30), 1024);
        AiProperties properties = new AiProperties("nvidia", defaultOptions, Map.of(), null);

        assertThat(properties.optionsFor(AiFeature.ISSUE_DETECTION)).isSameAs(defaultOptions);
    }

    @Test
    void invalidDefaultOptionsFailFast() {
        assertThatThrownBy(() -> new AiProperties(
                "nvidia",
                modelOptions("", Duration.ZERO, 0),
                Map.of(),
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model");
    }

    @Test
    void structuredOutputRetriesDefaultToTwoAndRejectExcessiveValues() {
        AiProperties defaults = new AiProperties(
                "nvidia",
                modelOptions("model", Duration.ofSeconds(1), 128),
                Map.of(),
                null
        );

        assertThat(defaults.structuredOutput().maxCorrectionRetries()).isEqualTo(2);
        assertThatThrownBy(() -> new AiProperties.StructuredOutputOptions(6))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-correction-retries");
    }

    @Test
    void rejectsUnsupportedProviderInsteadOfFallingBack() {
        assertThatThrownBy(() -> new AiProperties(
                "unknown",
                modelOptions("model", Duration.ofSeconds(1), 128),
                Map.of(),
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provider");
    }

    @Test
    void openAiProviderUsesExplicitDefaultsAndOverridesGenericModelOptions() {
        AiProperties properties = new AiProperties(
                "openai",
                modelOptions("nvidia-model", Duration.ofSeconds(30), 128),
                Map.of(),
                null,
                new AiProperties.OpenAiOptions(
                        "test-key", "https://api.openai.com", null, null, null, null, null
                )
        );

        AiProperties.ModelOptions options = properties.optionsFor(AiFeature.ISSUE_DETECTION);

        assertThat(options.model()).isEqualTo("gpt-5.6-luna");
        assertThat(options.timeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(options.maxOutputTokens()).isEqualTo(4096);
        assertThat(options.modelContextLimit()).isEqualTo(1_050_000);
        assertThat(properties.openai().reasoningEffort()).isEqualTo("medium");
        assertThat(properties.openai().clientBaseUrl()).isEqualTo("https://api.openai.com/v1");
    }

    @Test
    void openAiConfigurationRejectsMissingKeyUnsupportedReasoningAndInvalidUrl() {
        assertThatThrownBy(() -> openAiProperties(new AiProperties.OpenAiOptions(
                " ", null, null, null, null, null, null
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("api-key");

        assertThatThrownBy(() -> openAiProperties(new AiProperties.OpenAiOptions(
                "test-key", "https://api.openai.com", null, null, null, "extreme", null
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reasoning-effort");

        assertThatThrownBy(() -> openAiProperties(new AiProperties.OpenAiOptions(
                "test-key", "file:///tmp/openai", null, null, null, null, null
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("base-url");
    }

    @Test
    void rejectsProviderMaxTokenLimitOverflow() {
        AiProperties.ModelOptions overflow = new AiProperties.ModelOptions(
                "model",
                Duration.ofSeconds(1),
                100_000,
                128_001,
                300_000,
                8_192,
                AiProperties.ModelOptions.CONSERVATIVE_CHAR_V1,
                AiProperties.ModelOptions.REJECT_OVERSIZED_INPUT_V1,
                0,
                Duration.ZERO,
                Duration.ZERO
        );

        assertThatThrownBy(() -> new AiProperties("nvidia", overflow, Map.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("128000");
    }

    @Test
    void openAiAcceptsDocumentedMaximumOutputAndNormalizesBaseUrl() {
        AiProperties properties = openAiProperties(new AiProperties.OpenAiOptions(
                "test-key", "https://api.openai.com/v1/", null, null,
                128_000, null, null
        ));

        assertThat(properties.optionsFor(AiFeature.DRAFT_REVIEW).maxOutputTokens())
                .isEqualTo(128_000);
        assertThat(properties.openai().clientBaseUrl()).isEqualTo("https://api.openai.com/v1");
    }

    @Test
    void featureBudgetOverridesMergeIndependentlyAndAcceptExactContextBoundary() {
        AiProperties.ModelOptions defaults = new AiProperties.ModelOptions(
                "model",
                Duration.ofSeconds(30),
                80_000,
                4_096,
                100_000,
                8_192,
                AiProperties.ModelOptions.CONSERVATIVE_CHAR_V1,
                AiProperties.ModelOptions.REJECT_OVERSIZED_INPUT_V1,
                2,
                Duration.ofMillis(500),
                Duration.ofSeconds(8));
        AiProperties properties = new AiProperties(
                "nvidia",
                defaults,
                Map.of("opinion-synthesis", new AiProperties.FeatureOptions(
                        null,
                        null,
                        87_712,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)),
                null);

        AiProperties.ModelOptions resolved =
                properties.optionsFor(AiFeature.OPINION_SYNTHESIS);

        assertThat(resolved.maxInputTokens()).isEqualTo(87_712);
        assertThat(resolved.maxOutputTokens()).isEqualTo(4_096);
        assertThat(resolved.safetyMarginTokens()).isEqualTo(8_192);
        assertThat(resolved.maxInputTokens()
                + resolved.maxOutputTokens()
                + resolved.safetyMarginTokens()).isEqualTo(resolved.modelContextLimit());
    }

    @Test
    void invalidTokenSumFailsFast() {
        AiProperties.ModelOptions invalid = new AiProperties.ModelOptions(
                "model",
                Duration.ofSeconds(30),
                90_000,
                4_096,
                100_000,
                8_192,
                AiProperties.ModelOptions.CONSERVATIVE_CHAR_V1,
                AiProperties.ModelOptions.REJECT_OVERSIZED_INPUT_V1,
                2,
                Duration.ZERO,
                Duration.ZERO);

        assertThatThrownBy(() -> new AiProperties("nvidia", invalid, Map.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("context limit");
    }

    @Test
    void unknownEstimationOrSingleInputPolicyFailsFast() {
        assertThatThrownBy(() -> propertiesWithPolicies("unknown-v2",
                AiProperties.ModelOptions.REJECT_OVERSIZED_INPUT_V1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token-estimation-policy");

        assertThatThrownBy(() -> propertiesWithPolicies(
                AiProperties.ModelOptions.CONSERVATIVE_CHAR_V1, "truncate-v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("single-input-overflow-policy");
    }

    @Test
    void unknownFeatureOverrideFailsFast() {
        assertThatThrownBy(() -> new AiProperties(
                "nvidia",
                modelOptions("model", Duration.ofSeconds(1), 128),
                Map.of("future-feature", new AiProperties.FeatureOptions(
                        null, null, null, null, null, null,
                        null, null, null, null, null)),
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("지원하지 않는 기능");
    }

    @Test
    void allFourApprovedFeatureInputBudgetsResolveFromOverrides() {
        AiProperties properties = new AiProperties(
                "nvidia",
                modelOptions("model", Duration.ofSeconds(30), 4096),
                Map.of(
                        "issue-detection", featureBudget(64_000),
                        "opinion-synthesis", featureBudget(72_000),
                        "draft-generation", featureBudget(48_000),
                        "draft-review", featureBudget(64_000)),
                null);

        assertThat(properties.optionsFor(AiFeature.ISSUE_DETECTION).maxInputTokens())
                .isEqualTo(64_000);
        assertThat(properties.optionsFor(AiFeature.OPINION_SYNTHESIS).maxInputTokens())
                .isEqualTo(72_000);
        assertThat(properties.optionsFor(AiFeature.DRAFT_GENERATION).maxInputTokens())
                .isEqualTo(48_000);
        assertThat(properties.optionsFor(AiFeature.DRAFT_REVIEW).maxInputTokens())
                .isEqualTo(64_000);
    }

    private AiProperties propertiesWithPolicies(
            String estimationPolicy, String singleInputPolicy
    ) {
        return new AiProperties(
                "nvidia",
                new AiProperties.ModelOptions(
                        "model",
                        Duration.ofSeconds(30),
                        80_000,
                        4_096,
                        100_000,
                        8_192,
                        estimationPolicy,
                        singleInputPolicy,
                        2,
                        Duration.ZERO,
                        Duration.ZERO),
                Map.of(),
                null);
    }

    private AiProperties.FeatureOptions featureBudget(int maxInputTokens) {
        return new AiProperties.FeatureOptions(
                null,
                null,
                maxInputTokens,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private AiProperties.ModelOptions modelOptions(String model, Duration timeout, int maxOutputTokens) {
        return new AiProperties.ModelOptions(
                model,
                timeout,
                100_000,
                maxOutputTokens,
                131_072,
                8_192,
                AiProperties.ModelOptions.CONSERVATIVE_CHAR_V1,
                AiProperties.ModelOptions.REJECT_OVERSIZED_INPUT_V1,
                2,
                Duration.ofMillis(500),
                Duration.ofSeconds(8)
        );
    }

    private AiProperties openAiProperties(AiProperties.OpenAiOptions openAiOptions) {
        return new AiProperties(
                "openai",
                modelOptions("fallback", Duration.ofSeconds(30), 128),
                Map.of(),
                null,
                openAiOptions
        );
    }
}
