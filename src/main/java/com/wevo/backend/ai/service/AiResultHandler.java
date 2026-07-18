package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.AiProviderResponse;

@FunctionalInterface
public interface AiResultHandler<T> {

    AiProcessedResult<T> process(AiProviderResponse response);
}
