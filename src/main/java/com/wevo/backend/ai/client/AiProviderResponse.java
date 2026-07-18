package com.wevo.backend.ai.client;

/**
 * AI Provider 응답 본문과 운영 추적에 필요한 공통 메타데이터.
 */
public record AiProviderResponse(
        String content,
        AiUsageMetadata usageMetadata,
        String finishReason,
        int attemptCount
) {
}
