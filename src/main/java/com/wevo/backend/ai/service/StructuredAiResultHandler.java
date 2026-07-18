package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredAiProviderResponse;

@FunctionalInterface
public interface StructuredAiResultHandler<I, O> {

    AiProcessedResult<O> process(StructuredAiProviderResponse<I> response);
}
