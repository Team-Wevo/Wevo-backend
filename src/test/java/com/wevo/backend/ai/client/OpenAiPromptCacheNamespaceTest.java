package com.wevo.backend.ai.client;

import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.prompt.PromptTemplateId;
import com.wevo.backend.ai.prompt.RenderedPrompt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiPromptCacheNamespaceTest {

    private final OpenAiPromptCacheNamespace namespace = new OpenAiPromptCacheNamespace();

    @Test
    void separatesModelPromptSchemaAndStablePrefixWithoutEmbeddingTheirValues() {
        StructuredOutputDefinition<CacheOutput> schemaV1 = definition("cache-output", 1);
        RenderedPrompt promptV1 = prompt(1, "stable-system");
        String baseline = namespace.create(
                AiFeature.DRAFT_REVIEW, "gpt-5.6-luna", promptV1, schemaV1);

        assertThat(namespace.create(
                AiFeature.DRAFT_REVIEW, "gpt-5.6-luna", promptV1, schemaV1))
                .isEqualTo(baseline);
        assertThat(namespace.create(
                AiFeature.DRAFT_REVIEW, "gpt-5.6-luna-next", promptV1, schemaV1))
                .isNotEqualTo(baseline);
        assertThat(namespace.create(
                AiFeature.DRAFT_REVIEW, "gpt-5.6-luna", prompt(2, "stable-system"), schemaV1))
                .isNotEqualTo(baseline);
        assertThat(namespace.create(
                AiFeature.DRAFT_REVIEW, "gpt-5.6-luna", promptV1,
                definition("cache-output", 2)))
                .isNotEqualTo(baseline);
        assertThat(namespace.create(
                AiFeature.DRAFT_REVIEW, "gpt-5.6-luna", prompt(1, "changed-system"), schemaV1))
                .isNotEqualTo(baseline);
        assertThat(baseline).hasSize(64)
                .doesNotContain("gpt", "draft", "stable", "cache-output");
    }

    private RenderedPrompt prompt(int version, String system) {
        return new RenderedPrompt(
                new PromptTemplateId("cache-contract", version), system, "dynamic-user");
    }

    private StructuredOutputDefinition<CacheOutput> definition(String name, int version) {
        return StructuredOutputDefinition.of(
                new OutputSchemaId(name, version), CacheOutput.class);
    }

    private record CacheOutput(String value) {
    }
}
