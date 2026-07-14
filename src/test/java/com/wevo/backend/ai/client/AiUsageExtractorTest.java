package com.wevo.backend.ai.client;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;

import static org.assertj.core.api.Assertions.assertThat;

class AiUsageExtractorTest {

    private final AiUsageExtractor extractor = new AiUsageExtractor();

    @Test
    void extractsNormalAndCacheTokens() {
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .id("provider-1")
                .model("response-model")
                .usage(new DefaultUsage(10, 4, 14, null, 3L, 2L))
                .build();

        AiUsageMetadata usage = extractor.extract(metadata, "fallback-model");

        assertThat(usage).isEqualTo(new AiUsageMetadata(
                "provider-1", "response-model", 10L, 4L, 3L, 2L
        ));
        assertThat(usage.totalInputTokens()).isEqualTo(15L);
    }

    @Test
    void emptyUsageBecomesNullWhileExplicitZeroRemainsZero() {
        AiUsageMetadata missing = extractor.extract(ChatResponseMetadata.builder().build(), "fallback-model");
        AiUsageMetadata explicitZero = extractor.extract(
                ChatResponseMetadata.builder().usage(new DefaultUsage(0, 0, 0)).build(),
                "fallback-model"
        );

        assertThat(missing.inputTokens()).isNull();
        assertThat(missing.outputTokens()).isNull();
        assertThat(explicitZero.inputTokens()).isZero();
        assertThat(explicitZero.outputTokens()).isZero();
        assertThat(explicitZero.modelId()).isEqualTo("fallback-model");
    }
}
