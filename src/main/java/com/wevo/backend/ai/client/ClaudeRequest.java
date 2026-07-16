package com.wevo.backend.ai.client;

import com.wevo.backend.ai.domain.AiFeature;
import org.springframework.util.StringUtils;

/**
 * Claude에 전달할 provider 독립 요청.
 */
public record ClaudeRequest(
        AiFeature feature,
        String systemPrompt,
        String userPrompt
) {

    public ClaudeRequest {
        if (feature == null) {
            throw new IllegalArgumentException("feature는 필수입니다.");
        }
        if (!StringUtils.hasText(systemPrompt)) {
            throw new IllegalArgumentException("systemPrompt는 필수입니다.");
        }
        if (!StringUtils.hasText(userPrompt)) {
            throw new IllegalArgumentException("userPrompt는 필수입니다.");
        }
    }
}
