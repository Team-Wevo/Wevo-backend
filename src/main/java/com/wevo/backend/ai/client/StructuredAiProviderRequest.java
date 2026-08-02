package com.wevo.backend.ai.client;

import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.prompt.RenderedPrompt;

public record StructuredAiProviderRequest<T>(
        AiFeature feature,
        RenderedPrompt prompt,
        StructuredOutputDefinition<T> outputDefinition,
        StructuredOutputValidationContext validationContext,
        AiProviderExecutionPolicy executionPolicy
) {

    public StructuredAiProviderRequest(
            AiFeature feature,
            RenderedPrompt prompt,
            StructuredOutputDefinition<T> outputDefinition,
            StructuredOutputValidationContext validationContext
    ) {
        this(feature, prompt, outputDefinition, validationContext, null);
    }

    public StructuredAiProviderRequest {
        if (feature == null || prompt == null || outputDefinition == null) {
            throw new IllegalArgumentException("feature, prompt, outputDefinition은 필수입니다.");
        }
        validationContext = validationContext == null
                ? StructuredOutputValidationContext.empty()
                : validationContext;
    }

    public StructuredAiProviderRequest<T> withExecutionPolicy(AiProviderExecutionPolicy policy) {
        return new StructuredAiProviderRequest<>(
                feature, prompt, outputDefinition, validationContext, policy);
    }
}
