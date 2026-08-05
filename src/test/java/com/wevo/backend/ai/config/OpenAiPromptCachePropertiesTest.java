package com.wevo.backend.ai.config;

import com.wevo.backend.ai.domain.AiFeature;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiPromptCachePropertiesTest {

    @Test
    void defaultsToRollbackSafeImplicitBaseline() {
        OpenAiPromptCacheProperties properties =
                new OpenAiPromptCacheProperties(null, null, null, null);

        assertThat(properties.isOptimizationEnabled()).isFalse();
        assertThat(properties.explicitFeatures()).isEmpty();
        assertThat(properties.ttl()).isEqualTo("30m");
        assertThat(properties.profileId()).isEqualTo("implicit-baseline");
    }

    @Test
    void acceptsOnlyKnownFeaturesModelsAndTheSupportedTtl() {
        OpenAiPromptCacheProperties properties = new OpenAiPromptCacheProperties(
                true,
                Set.of("draft-review"),
                Set.of("gpt-5.6-luna"),
                "30m"
        );

        assertThat(properties.requestsExplicit(AiFeature.DRAFT_REVIEW)).isTrue();
        assertThat(properties.supportsExplicitModel("gpt-5.6-luna")).isTrue();
        assertThat(properties.profileId()).isEqualTo("explicit-candidate");
        assertThatThrownBy(() -> new OpenAiPromptCacheProperties(
                true, Set.of("unknown"), Set.of("gpt-5.6-luna"), "30m"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("explicit-features");
        assertThatThrownBy(() -> new OpenAiPromptCacheProperties(
                true, Set.of(), Set.of("gpt-5.6-luna"), "24h"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ttl");
    }
}
