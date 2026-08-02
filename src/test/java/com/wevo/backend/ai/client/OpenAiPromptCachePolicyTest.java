package com.wevo.backend.ai.client;

import com.wevo.backend.ai.config.OpenAiPromptCacheProperties;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.prompt.PromptTemplateId;
import com.wevo.backend.ai.prompt.RenderedPrompt;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiPromptCachePolicyTest {

    @Test
    void disabledConfigurationKeepsTheProviderImplicitBaselineUntouched() {
        OpenAiPromptCachePolicy policy = policy(false, Set.of(), Set.of());

        assertThat(policy.decide(request(AiFeature.DRAFT_REVIEW), "gpt-5.6-luna").enabled())
                .isFalse();
    }

    @Test
    void enablesRoutingKeyButLimitsExplicitFieldsToApprovedFeatureAndModel() {
        OpenAiPromptCachePolicy policy = policy(
                true, Set.of("draft-review"), Set.of("gpt-5.6-luna"));

        OpenAiPromptCacheDecision implicit =
                policy.decide(request(AiFeature.ISSUE_DETECTION), "gpt-5.6-luna");
        OpenAiPromptCacheDecision explicit =
                policy.decide(request(AiFeature.DRAFT_REVIEW), "gpt-5.6-luna");

        assertThat(implicit.enabled()).isTrue();
        assertThat(implicit.explicit()).isFalse();
        assertThat(explicit.enabled()).isTrue();
        assertThat(explicit.explicit()).isTrue();
        assertThat(explicit.ttl()).isEqualTo("30m");
        assertThatThrownBy(() -> policy.decide(
                request(AiFeature.DRAFT_REVIEW), "legacy-model"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("검증되지 않은");
    }

    private OpenAiPromptCachePolicy policy(
            boolean enabled,
            Set<String> features,
            Set<String> models
    ) {
        return new OpenAiPromptCachePolicy(
                new OpenAiPromptCacheProperties(enabled, features, models, "30m"),
                new OpenAiPromptCacheNamespace()
        );
    }

    private StructuredAiProviderRequest<CacheOutput> request(AiFeature feature) {
        return new StructuredAiProviderRequest<>(
                feature,
                new RenderedPrompt(
                        new PromptTemplateId("cache-contract", 1),
                        "stable system", "dynamic user"),
                StructuredOutputDefinition.of(
                        new OutputSchemaId("cache-contract", 1), CacheOutput.class),
                StructuredOutputValidationContext.empty()
        );
    }

    private record CacheOutput(String value) {
    }
}
