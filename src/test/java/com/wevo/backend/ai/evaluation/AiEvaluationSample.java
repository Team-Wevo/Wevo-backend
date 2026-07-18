package com.wevo.backend.ai.evaluation;

import com.wevo.backend.ai.client.AiUsageMetadata;
import com.wevo.backend.ai.domain.AiCostSnapshot;

import java.util.List;

public record AiEvaluationSample(
        String fixtureId,
        AiEvaluationOutcome outcome,
        AiEvaluationCandidate candidate,
        AiUsageMetadata usage,
        AiCostSnapshot cost,
        int attemptCount,
        long latencyMillis,
        List<SchemaViolationKind> schemaViolations
) {

    public AiEvaluationSample {
        if (fixtureId == null || fixtureId.isBlank() || outcome == null || attemptCount < 0 || latencyMillis < 0) {
            throw new IllegalArgumentException("유효한 fixture ID, outcome, attemptCount, latency가 필요합니다.");
        }
        schemaViolations = schemaViolations == null ? List.of() : List.copyOf(schemaViolations);
    }

    static AiEvaluationSample from(
            String fixtureId,
            AiEvaluationObservation observation,
            long latencyMillis
    ) {
        return new AiEvaluationSample(
                fixtureId,
                observation.outcome(),
                observation.candidate(),
                observation.usage(),
                observation.cost(),
                observation.attemptCount(),
                latencyMillis,
                observation.schemaViolations()
        );
    }

    static AiEvaluationSample budgetExhausted(String fixtureId) {
        return new AiEvaluationSample(
                fixtureId,
                AiEvaluationOutcome.BUDGET_EXHAUSTED,
                null,
                null,
                null,
                0,
                0,
                List.of()
        );
    }

    public boolean providerCallStarted() {
        return attemptCount > 0;
    }
}
