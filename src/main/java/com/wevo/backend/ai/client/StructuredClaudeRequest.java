package com.wevo.backend.ai.client;

import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.prompt.RenderedPrompt;

public record StructuredClaudeRequest<T>(
        AiFeature feature,
        RenderedPrompt prompt,
        StructuredOutputDefinition<T> outputDefinition,
        StructuredOutputValidationContext validationContext
) {

    public StructuredClaudeRequest {
        if (feature == null || prompt == null || outputDefinition == null) {
            throw new IllegalArgumentException("feature, prompt, outputDefinition은 필수입니다.");
        }
        validationContext = validationContext == null
                ? StructuredOutputValidationContext.empty()
                : validationContext;
    }
}
