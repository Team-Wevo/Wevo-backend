package com.wevo.backend.ai.client;

import com.openai.core.JsonValue;
import com.openai.models.completions.CompletionUsage;
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

        AiUsageMetadata usage = extractor.extract(metadata, "fallback-model", "test-provider");

        assertThat(usage).isEqualTo(new AiUsageMetadata(
                "test-provider", "provider-1", "response-model", 5L, 4L, 3L, 2L
        ));
        assertThat(usage.totalInputTokens()).isEqualTo(10L);
    }

    @Test
    void prefersProviderRequestIdAndExtractsOpenAiReasoningTokens() {
        CompletionUsage nativeUsage = CompletionUsage.builder()
                .promptTokens(12)
                .completionTokens(7)
                .totalTokens(19)
                .completionTokensDetails(
                        CompletionUsage.CompletionTokensDetails.builder().reasoningTokens(4).build()
                )
                .build();
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .id("chatcmpl-1")
                .keyValue("request_id", "req-1")
                .model("gpt-5.6-luna-snapshot")
                .usage(new DefaultUsage(12, 7, 19, nativeUsage, 3L, 0L))
                .build();

        AiUsageMetadata usage = extractor.extract(metadata, "gpt-5.6-luna", "openai");

        assertThat(usage.providerRequestId()).isEqualTo("req-1");
        assertThat(usage.modelId()).isEqualTo("gpt-5.6-luna-snapshot");
        assertThat(usage.inputTokens()).isEqualTo(9L);
        assertThat(usage.cacheReadInputTokens()).isEqualTo(3L);
        assertThat(usage.reasoningTokens()).isEqualTo(4L);
    }

    @Test
    void extractsOpenAiCacheWriteFromNativePromptTokenDetails() {
        CompletionUsage nativeUsage = CompletionUsage.builder()
                .promptTokens(12)
                .completionTokens(2)
                .totalTokens(14)
                .promptTokensDetails(CompletionUsage.PromptTokensDetails.builder()
                        .cachedTokens(3)
                        .putAdditionalProperty("cache_write_tokens", JsonValue.from(2L))
                        .build())
                .build();
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model("gpt-5.6-luna")
                .usage(new DefaultUsage(12, 2, 14, nativeUsage, 3L, null))
                .build();

        AiUsageMetadata usage = extractor.extract(metadata, "fallback", "openai");

        assertThat(usage.inputTokens()).isEqualTo(7L);
        assertThat(usage.cacheReadInputTokens()).isEqualTo(3L);
        assertThat(usage.cacheWriteInputTokens()).isEqualTo(2L);
        assertThat(usage.totalInputTokens()).isEqualTo(12L);
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
