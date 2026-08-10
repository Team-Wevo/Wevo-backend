package com.wevo.backend.ai.evaluation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiEvaluationGateTest {

    private final AiEvaluationFixtureLoader loader = new AiEvaluationFixtureLoader();
    private final AiEvaluationMetricsCalculator calculator = new AiEvaluationMetricsCalculator();
    private final AiEvaluationGate gate = new AiEvaluationGate();

    @Test
    void doesNotUseRatesAsGateBelowMinimumSampleAndRequiresHumanQualityReview() {
        AiEvaluationFixture fixture = loader.load(
                "ai/evaluation/issue-detection/all-agreed-proposal.json"
        );
        AiEvaluationMetrics metrics = calculator.calculate(List.of(fixture), List.of());

        AiEvaluationGate.Result result = gate.evaluate(metrics, 20);

        assertThat(result.automaticStatus())
                .isEqualTo(AiEvaluationGate.AutomaticStatus.INSUFFICIENT_SAMPLES);
        assertThat(result.overallStatus()).isEqualTo(AiEvaluationGate.OverallStatus.REVIEW_REQUIRED);
        assertThat(result.reasons()).contains("INSUFFICIENT_SAMPLES", "HUMAN_REVIEW_REQUIRED");
    }

    @Test
    void evidenceIntegrityFailureAlwaysFailsEvenWithSmallSample() {
        AiEvaluationFixture fixture = loader.load(
                "ai/evaluation/issue-detection/all-agreed-proposal.json"
        );
        AiEvaluationCandidate candidate = new AiEvaluationCandidate(
                List.of(),
                List.of(new AiEvaluationCandidate.Claim(
                        "합성 claim", true, java.util.Set.of("opinion-missing")
                )),
                java.util.Set.of(),
                0,
                0
        );
        AiEvaluationSample sample = new AiEvaluationSample(
                fixture.metadata().id(), AiEvaluationOutcome.SUCCESS, candidate,
                new com.wevo.backend.ai.client.AiUsageMetadata(
                        "test-provider", "request", "model", 1L, 1L, null, null
                ),
                com.wevo.backend.ai.domain.AiCostSnapshot.unpriced("trial"),
                1, 1, List.of()
        );
        AiEvaluationMetrics metrics = calculator.calculate(List.of(fixture), List.of(sample));

        AiEvaluationGate.Result result = gate.evaluate(metrics, 20);

        assertThat(result.automaticStatus()).isEqualTo(AiEvaluationGate.AutomaticStatus.FAIL);
        assertThat(result.overallStatus()).isEqualTo(AiEvaluationGate.OverallStatus.FAIL);
        assertThat(result.reasons()).contains("UNKNOWN_EVIDENCE_ID");
    }
}
