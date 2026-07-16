package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.ClaudeResponse;

@FunctionalInterface
public interface AiResultHandler<T> {

    AiProcessedResult<T> process(ClaudeResponse response);
}
