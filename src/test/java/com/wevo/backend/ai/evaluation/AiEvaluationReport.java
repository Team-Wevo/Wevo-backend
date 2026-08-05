package com.wevo.backend.ai.evaluation;

import java.util.List;
import java.util.Map;

public record AiEvaluationReport(
        String reportSchemaVersion,
        AiEvaluationRunMetadata run,
        AiEvaluationMetrics metrics,
        AiEvaluationGate.Result gate,
        AiEvaluationHumanReview humanReview,
        Map<String, Double> baselineDeltas,
        List<FixtureResult> fixtureResults,
        List<Failure> failures
) {

    public record FixtureResult(
            String fixtureId,
            AiEvaluationOutcome outcome,
            long latencyMillis,
            int attemptCount,
            String providerId,
            String providerRequestId,
            String modelId,
            Long inputTokens,
            Long outputTokens,
            Long cacheReadTokens,
            Long cacheWriteTokens,
            Long reasoningTokens,
            java.math.BigDecimal estimatedCost,
            AiEvaluationMetrics.CostStatus costStatus
    ) {
    }

    public record Failure(String fixtureId, AiEvaluationOutcome reason) {
    }
}
