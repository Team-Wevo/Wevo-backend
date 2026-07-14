package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredClaudeResponse;

@FunctionalInterface
public interface StructuredAiResultHandler<I, O> {

    AiProcessedResult<O> process(StructuredClaudeResponse<I> response);
}
