package com.wevo.backend.ai.client;

import com.openai.core.JsonValue;
import com.openai.models.completions.CompletionUsage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AiUsageExtractor {

    public AiUsageMetadata extract(ChatResponseMetadata metadata, String fallbackModelId, String providerId) {
        if (metadata == null) {
            return new AiUsageMetadata(
                    normalize(providerId, 30), null, normalize(fallbackModelId, 100),
                    null, null, null, null, null
            );
        }

        Usage usage = metadata.getUsage();
        boolean unavailable = usage == null || usage instanceof EmptyUsage;
        String modelId = StringUtils.hasText(metadata.getModel())
                ? metadata.getModel()
                : fallbackModelId;
        Long cacheRead = unavailable ? null : usage.getCacheReadInputTokens();
        Long cacheWrite = unavailable ? null : firstNonNull(
                usage.getCacheWriteInputTokens(), nativeCacheWriteTokens(usage.getNativeUsage())
        );
        Long promptTokens = unavailable ? null : toLong(usage.getPromptTokens());
        Long uncachedInputTokens = subtractCachedTokens(promptTokens, cacheRead, cacheWrite);

        return new AiUsageMetadata(
                normalize(providerId, 30),
                normalize(providerRequestId(metadata), 200),
                normalize(modelId, 100),
                uncachedInputTokens,
                unavailable ? null : toLong(usage.getCompletionTokens()),
                cacheRead,
                cacheWrite,
                unavailable ? null : nativeReasoningTokens(usage.getNativeUsage())
        );
    }

    public AiUsageMetadata extract(ChatResponseMetadata metadata, String fallbackModelId) {
        return extract(metadata, fallbackModelId, null);
    }

    private Long toLong(Integer value) {
        return value == null ? null : value.longValue();
    }

    private String providerRequestId(ChatResponseMetadata metadata) {
        Object requestId = metadata.get("request_id");
        return requestId instanceof String text && StringUtils.hasText(text)
                ? text
                : metadata.getId();
    }

    private Long subtractCachedTokens(Long promptTokens, Long cacheRead, Long cacheWrite) {
        if (promptTokens == null) {
            return null;
        }
        long uncached = promptTokens
                - (cacheRead == null ? 0L : cacheRead)
                - (cacheWrite == null ? 0L : cacheWrite);
        return Math.max(0L, uncached);
    }

    private Long nativeReasoningTokens(Object nativeUsage) {
        if (nativeUsage instanceof CompletionUsage completionUsage) {
            return completionUsage.completionTokensDetails()
                    .flatMap(CompletionUsage.CompletionTokensDetails::reasoningTokens)
                    .orElse(null);
        }
        return null;
    }

    private Long nativeCacheWriteTokens(Object nativeUsage) {
        if (!(nativeUsage instanceof CompletionUsage completionUsage)) {
            return null;
        }
        Long topLevel = jsonLong(completionUsage._additionalProperties().get("cache_write_tokens"));
        if (topLevel != null) {
            return topLevel;
        }
        return completionUsage.promptTokensDetails()
                .map(details -> jsonLong(details._additionalProperties().get("cache_write_tokens")))
                .orElse(null);
    }

    private Long jsonLong(JsonValue value) {
        if (value == null) {
            return null;
        }
        try {
            return value.convert(Long.class);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Long firstNonNull(Long first, Long second) {
        return first != null ? first : second;
    }

    private String normalize(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
