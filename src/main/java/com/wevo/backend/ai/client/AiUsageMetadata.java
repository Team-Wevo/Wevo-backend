package com.wevo.backend.ai.client;

/**
 * Provider 응답에서 추출한 사용량 메타데이터.
 * 원본 prompt나 completion은 포함하지 않는다.
 */
public record AiUsageMetadata(
        String providerRequestId,
        String modelId,
        Long inputTokens,
        Long outputTokens,
        Long cacheReadInputTokens,
        Long cacheWriteInputTokens
) {

    public Long totalInputTokens() {
        if (inputTokens == null) {
            return null;
        }
        return inputTokens
                + (cacheReadInputTokens == null ? 0L : cacheReadInputTokens)
                + (cacheWriteInputTokens == null ? 0L : cacheWriteInputTokens);
    }
}
