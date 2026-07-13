package com.wevo.backend.ai.client;

import org.springframework.util.StringUtils;

/**
 * Claude에 전달할 provider 독립 요청.
 */
public record ClaudeRequest(
        String featureName,
        String systemPrompt,
        String userPrompt
) {

    public ClaudeRequest {
        if (!StringUtils.hasText(featureName)) {
            throw new IllegalArgumentException("featureName은 필수입니다.");
        }
        if (!StringUtils.hasText(systemPrompt)) {
            throw new IllegalArgumentException("systemPrompt는 필수입니다.");
        }
        if (!StringUtils.hasText(userPrompt)) {
            throw new IllegalArgumentException("userPrompt는 필수입니다.");
        }
    }
}
