package com.wevo.backend.ai.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiPropertiesTest {

    @Test
    void featureOptionsOverrideOnlyConfiguredValues() {
        AiProperties properties = new AiProperties(
                new AiProperties.ModelOptions("default-model", Duration.ofSeconds(60), 4096),
                Map.of("draft", new AiProperties.FeatureOptions("draft-model", null, 2048))
        );

        AiProperties.ModelOptions options = properties.optionsFor("draft");

        assertThat(options.model()).isEqualTo("draft-model");
        assertThat(options.timeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(options.maxOutputTokens()).isEqualTo(2048);
    }

    @Test
    void unknownFeatureUsesDefaultOptions() {
        AiProperties.ModelOptions defaultOptions =
                new AiProperties.ModelOptions("default-model", Duration.ofSeconds(30), 1024);
        AiProperties properties = new AiProperties(defaultOptions, Map.of());

        assertThat(properties.optionsFor("unknown")).isSameAs(defaultOptions);
    }

    @Test
    void invalidDefaultOptionsFailFast() {
        assertThatThrownBy(() -> new AiProperties(
                new AiProperties.ModelOptions("", Duration.ZERO, 0),
                Map.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model");
    }
}
