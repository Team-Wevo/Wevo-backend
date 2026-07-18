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
                        "draft-model", null, 2048, null, null, null
                )),
                null
        );

        AiProperties.ModelOptions options = properties.optionsFor(AiFeature.DRAFT_GENERATION);

        assertThat(options.model()).isEqualTo("draft-model");
        assertThat(options.timeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(options.maxOutputTokens()).isEqualTo(2048);
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
    void rejectsProviderMaxTokenLimitOverflow() {
        AiProperties.ModelOptions overflow = new AiProperties.ModelOptions(
                "model", Duration.ofSeconds(1), 65_537, 0, Duration.ZERO, Duration.ZERO
        );

        assertThatThrownBy(() -> new AiProperties("nvidia", overflow, Map.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("65536");
    }

    private AiProperties.ModelOptions modelOptions(String model, Duration timeout, int maxOutputTokens) {
        return new AiProperties.ModelOptions(
                model, timeout, maxOutputTokens, 2, Duration.ofMillis(500), Duration.ofSeconds(8)
        );
    }
}
