package com.wevo.backend.ai.client;

import com.wevo.backend.ai.prompt.PromptTemplateId;

public record StructuredClaudeResponse<T>(
        T result,
        PromptTemplateId promptId,
        OutputSchemaId schemaId,
        AiUsageMetadata usageMetadata,
        String finishReason,
        int attemptCount
) {

    public StructuredClaudeResponse {
        if (result == null || promptId == null || schemaId == null || attemptCount <= 0) {
            throw new IllegalArgumentException("result, promptId, schemaId와 유효한 attemptCount는 필수입니다.");
        }
    }
}
