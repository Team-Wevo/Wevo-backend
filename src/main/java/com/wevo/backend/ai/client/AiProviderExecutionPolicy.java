package com.wevo.backend.ai.client;

/** AiJob에 고정된 실행 정책을 Provider 요청까지 전달한다. */
public record AiProviderExecutionPolicy(
        String modelId,
        Integer maxOutputTokens,
        String reasoningEffort
) {
    public AiProviderExecutionPolicy {
        if (modelId == null || modelId.isBlank() || modelId.length() > 100) {
            throw new IllegalArgumentException("실행 snapshot modelId가 올바르지 않습니다.");
        }
        if (maxOutputTokens == null || maxOutputTokens <= 0) {
            throw new IllegalArgumentException("실행 snapshot maxOutputTokens는 1 이상이어야 합니다.");
        }
        if (reasoningEffort == null || reasoningEffort.isBlank() || reasoningEffort.length() > 30) {
            throw new IllegalArgumentException("실행 snapshot reasoningEffort가 올바르지 않습니다.");
        }
    }
}
