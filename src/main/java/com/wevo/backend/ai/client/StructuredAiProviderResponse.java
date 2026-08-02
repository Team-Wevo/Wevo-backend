package com.wevo.backend.ai.client;

import com.wevo.backend.ai.prompt.PromptTemplateId;

public record StructuredAiProviderResponse<T>(
        T result,
        PromptTemplateId promptId,
        OutputSchemaId schemaId,
        AiUsageMetadata usageMetadata,
        String finishReason,
        int attemptCount,
        int providerRetryCount,
        int correctionRetryCount
) {

    public StructuredAiProviderResponse(
            T result,
            PromptTemplateId promptId,
            OutputSchemaId schemaId,
            AiUsageMetadata usageMetadata,
            String finishReason,
            int attemptCount
    ) {
        this(result, promptId, schemaId, usageMetadata, finishReason, attemptCount, 0, 0);
    }

    public StructuredAiProviderResponse {
        if (result == null || promptId == null || schemaId == null || attemptCount <= 0
                || providerRetryCount < 0 || correctionRetryCount < 0) {
            throw new IllegalArgumentException("result, promptId, schemaId와 유효한 attemptCount는 필수입니다.");
        }
    }
}
