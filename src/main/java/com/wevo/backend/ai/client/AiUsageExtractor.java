package com.wevo.backend.ai.client;

import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AiUsageExtractor {

    public AiUsageMetadata extract(ChatResponseMetadata metadata, String fallbackModelId) {
        if (metadata == null) {
            return new AiUsageMetadata(null, normalize(fallbackModelId, 100), null, null, null, null);
        }

        Usage usage = metadata.getUsage();
        boolean unavailable = usage == null || usage instanceof EmptyUsage;
        String modelId = StringUtils.hasText(metadata.getModel())
                ? metadata.getModel()
                : fallbackModelId;

        return new AiUsageMetadata(
                normalize(metadata.getId(), 200),
                normalize(modelId, 100),
                unavailable ? null : toLong(usage.getPromptTokens()),
                unavailable ? null : toLong(usage.getCompletionTokens()),
                unavailable ? null : usage.getCacheReadInputTokens(),
                unavailable ? null : usage.getCacheWriteInputTokens()
        );
    }

    private Long toLong(Integer value) {
        return value == null ? null : value.longValue();
    }

    private String normalize(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
