package com.wevo.backend.ai.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NvidiaProviderPropertiesTest {

    @Test
    void defaultsMistralOptionsAndValidatesValues() {
        NvidiaProviderProperties defaults = new NvidiaProviderProperties(null, null);

        assertThat(defaults.temperature()).isEqualTo(0.1d);
        assertThat(defaults.reasoningEffort()).isEqualTo("none");
        assertThat(new NvidiaProviderProperties(0.2d, "HIGH").reasoningEffort()).isEqualTo("high");
        assertThatThrownBy(() -> new NvidiaProviderProperties(1.1d, "none"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("temperature");
        assertThatThrownBy(() -> new NvidiaProviderProperties(0.1d, "medium"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reasoning-effort");
    }
}
