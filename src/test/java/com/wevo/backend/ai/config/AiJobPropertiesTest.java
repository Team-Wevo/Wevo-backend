package com.wevo.backend.ai.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiJobPropertiesTest {

    @Test
    void defaultsToThreeExecutionsAndRejectsNonPositiveLimit() {
        assertThat(new AiJobProperties(null).maxExecutionsPerKey()).isEqualTo(3);
        assertThatThrownBy(() -> new AiJobProperties(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
