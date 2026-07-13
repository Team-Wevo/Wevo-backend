package com.wevo.backend.ai.client;

/**
 * Claude 응답 본문과 운영 추적에 필요한 최소 메타데이터.
 */
public record ClaudeResponse(
        String content,
        String requestId,
        String model,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Long cacheReadInputTokens,
        Long cacheWriteInputTokens,
        String finishReason
) {
}
