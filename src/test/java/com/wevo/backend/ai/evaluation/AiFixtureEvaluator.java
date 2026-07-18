package com.wevo.backend.ai.evaluation;

@FunctionalInterface
public interface AiFixtureEvaluator {

    AiEvaluationObservation evaluate(AiEvaluationFixture fixture);
}
