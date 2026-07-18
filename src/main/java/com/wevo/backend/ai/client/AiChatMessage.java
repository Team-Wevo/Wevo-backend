package com.wevo.backend.ai.client;

import org.springframework.util.StringUtils;

/**
 * Provider SDK 타입을 노출하지 않는 대화 메시지 계약.
 */
public record AiChatMessage(
        Role role,
        String content
) {

    public AiChatMessage {
        if (role == null) {
            throw new IllegalArgumentException("role은 필수입니다.");
        }
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("content는 필수입니다.");
        }
    }

    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT
    }
}
