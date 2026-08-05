package com.wevo.backend.ai.client;

import com.wevo.backend.ai.domain.AiFeature;

import java.util.List;

/**
 * 선택된 AI Provider에 전달하는 provider 중립 요청.
 */
public record AiProviderRequest(
        AiFeature feature,
        List<AiChatMessage> messages,
        AiProviderExecutionPolicy executionPolicy
) {

    public AiProviderRequest(AiFeature feature, List<AiChatMessage> messages) {
        this(feature, messages, null);
    }

    public AiProviderRequest(AiFeature feature, String systemPrompt, String userPrompt) {
        this(feature, List.of(
                new AiChatMessage(AiChatMessage.Role.SYSTEM, systemPrompt),
                new AiChatMessage(AiChatMessage.Role.USER, userPrompt)
        ), null);
    }

    public AiProviderRequest {
        if (feature == null) {
            throw new IllegalArgumentException("feature는 필수입니다.");
        }
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages는 하나 이상이어야 합니다.");
        }
        if (messages.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("messages에는 null을 포함할 수 없습니다.");
        }
        messages = List.copyOf(messages);
    }

    public AiProviderRequest withExecutionPolicy(AiProviderExecutionPolicy policy) {
        return new AiProviderRequest(feature, messages, policy);
    }
}
