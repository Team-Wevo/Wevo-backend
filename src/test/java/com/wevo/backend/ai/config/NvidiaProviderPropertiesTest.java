package com.wevo.backend.ai.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NvidiaProviderPropertiesTest {

    @Test
    void defaultsTemperatureToRecommendedValueAndValidatesRange() {
        assertThat(new NvidiaProviderProperties(null).temperature()).isEqualTo(1.0d);
        assertThatThrownBy(() -> new NvidiaProviderProperties(1.1d))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("temperature");
    }
}
