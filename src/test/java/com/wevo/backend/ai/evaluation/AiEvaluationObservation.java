package com.wevo.backend.ai.evaluation;

import com.wevo.backend.ai.client.AiUsageMetadata;
import com.wevo.backend.ai.domain.AiCostSnapshot;

import java.util.List;

public record AiEvaluationObservation(
        AiEvaluationOutcome outcome,
        AiEvaluationCandidate candidate,
        AiUsageMetadata usage,
        AiCostSnapshot cost,
        int attemptCount,
        List<SchemaViolationKind> schemaViolations
) {

    public AiEvaluationObservation {
        if (outcome == null || attemptCount < 0) {
            throw new IllegalArgumentException("평가 outcome과 0 이상의 attemptCount는 필수입니다.");
        }
        if (outcome == AiEvaluationOutcome.SUCCESS && (candidate == null || attemptCount == 0)) {
            throw new IllegalArgumentException("성공 평가에는 candidate와 attemptCount가 필요합니다.");
        }
        schemaViolations = schemaViolations == null ? List.of() : List.copyOf(schemaViolations);
    }

    public static AiEvaluationObservation success(
            AiEvaluationCandidate candidate,
            AiUsageMetadata usage,
            AiCostSnapshot cost,
            int attemptCount
    ) {
        return new AiEvaluationObservation(
                AiEvaluationOutcome.SUCCESS, candidate, usage, cost, attemptCount, List.of()
        );
    }

    public static AiEvaluationObservation failure(
            AiEvaluationOutcome outcome,
            AiUsageMetadata usage,
            AiCostSnapshot cost,
            int attemptCount,
            List<SchemaViolationKind> violations
    ) {
        return new AiEvaluationObservation(outcome, null, usage, cost, attemptCount, violations);
    }
}
