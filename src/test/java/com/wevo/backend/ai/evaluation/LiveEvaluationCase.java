package com.wevo.backend.ai.evaluation;

import com.wevo.backend.ai.client.StructuredAiProviderRequest;

public interface LiveEvaluationCase<T> {

    StructuredAiProviderRequest<T> requestFor(AiEvaluationFixture fixture);

    AiEvaluationCandidate normalize(AiEvaluationFixture fixture, T result);
}
