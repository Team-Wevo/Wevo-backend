package com.wevo.backend.ai.evaluation;

import com.wevo.backend.ai.client.AiUsageMetadata;
import com.wevo.backend.ai.domain.AiCostSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AiEvaluationMetricsCalculatorTest {

    private final AiEvaluationFixtureLoader loader = new AiEvaluationFixtureLoader();
    private final AiEvaluationMetricsCalculator calculator = new AiEvaluationMetricsCalculator();

    @Test
    void calculatesSchemaQualityEvidencePrecisionRecallAndLatencyMetrics() {
        AiEvaluationFixture conflict = fixture("direct-conflict-proposal.json");
        AiEvaluationFixture gap = fixture("missing-information-gap-proposal.json");
        AiEvaluationCandidate conflictCandidate = new AiEvaluationCandidate(
                List.of(new AiEvaluationCandidate.DetectedIssue(
                        AiEvaluationFixture.IssueType.CONFLICT,
                        Set.of("opinion-a", "opinion-b")
                )),
                List.of(new AiEvaluationCandidate.Claim(
                        "공연 장비 배분 비율은 결정이 필요하다.", true, Set.of("opinion-a")
                )),
                Set.of(),
                0,
                0
        );
        AiEvaluationCandidate gapCandidate = new AiEvaluationCandidate(
                List.of(new AiEvaluationCandidate.DetectedIssue(
                        AiEvaluationFixture.IssueType.CONFLICT,
                        Set.of("opinion-a")
                )),
                List.of(new AiEvaluationCandidate.Claim(
                        "총예산은 500만원이다.", true, Set.of()
                )),
                Set.of("review-budget-total-missing", "review-false-positive"),
                1,
                2
        );
        List<AiEvaluationSample> samples = List.of(
                success(conflict, conflictCandidate, usage(10L, 4L, 2L, 1L), priced("0.01"), 10),
                success(gap, gapCandidate, usage(20L, 6L, 3L, 1L), priced("0.02"), 100)
        );

        AiEvaluationMetrics metrics = calculator.calculate(List.of(conflict, gap), samples);

        assertThat(metrics.schemaValidRate().value()).isEqualTo(1.0d);
        assertThat(metrics.evidenceCoverage().value()).isEqualTo(0.5d);
        assertThat(metrics.issuePrecision().value()).isEqualTo(0.5d);
        assertThat(metrics.issueRecall().value()).isEqualTo(0.5d);
        assertThat(metrics.reviewGoldRecall().value()).isEqualTo(1.0d);
        assertThat(metrics.reviewFalsePositiveRate().value()).isEqualTo(0.5d);
        assertThat(metrics.mergedConflictCount().value()).isEqualTo(1);
        assertThat(metrics.unverifiedFactCount().value()).isEqualTo(2);
        assertThat(metrics.forbiddenClaimCount().value()).isEqualTo(1);
        assertThat(metrics.inputTokens().total()).isEqualTo(30L);
        assertThat(metrics.cacheReadTokens().total()).isEqualTo(5L);
        assertThat(metrics.cost().status()).isEqualTo(AiEvaluationMetrics.CostStatus.PRICED);
        assertThat(metrics.cost().estimatedCost()).isEqualByComparingTo("0.03");
        assertThat(metrics.latencyP50Millis()).isEqualTo(10L);
        assertThat(metrics.latencyP95Millis()).isEqualTo(100L);
    }

    @Test
    void distinguishesZeroSamplesFromMeasuredZeroAndKeepsNullableUsageUnpriced() {
        AiEvaluationFixture fixture = fixture("all-agreed-proposal.json");

        AiEvaluationMetrics empty = calculator.calculate(List.of(fixture), List.of());

        assertThat(empty.schemaValidRate().value()).isNull();
        assertThat(empty.schemaValidRate().status())
                .isEqualTo(AiEvaluationMetrics.MeasurementStatus.NOT_MEASURABLE);
        assertThat(empty.latencyP50Millis()).isNull();
        assertThat(empty.inputTokens().total()).isNull();
        assertThat(empty.missingRequiredFieldCount().value()).isNull();
        assertThat(empty.unknownEvidenceIdCount().status())
                .isEqualTo(AiEvaluationMetrics.MeasurementStatus.NOT_MEASURABLE);

        AiEvaluationSample sample = success(
                fixture,
                AiEvaluationCandidate.empty(),
                usage(null, null, null, null),
                AiCostSnapshot.unpriced("trial-unpriced"),
                7
        );
        AiEvaluationMetrics unpriced = calculator.calculate(List.of(fixture), List.of(sample));

        assertThat(unpriced.inputTokens().total()).isNull();
        assertThat(unpriced.outputTokens().status())
                .isEqualTo(AiEvaluationMetrics.MeasurementStatus.NOT_MEASURABLE);
        assertThat(unpriced.cost().estimatedCost()).isNull();
        assertThat(unpriced.cost().status()).isEqualTo(AiEvaluationMetrics.CostStatus.UNPRICED);
        assertThat(unpriced.unknownEvidenceIdCount().value()).isZero();
    }

    @Test
    void preservesPartialTokenMeasurementWithoutPublishingAnIncompleteTotal() {
        AiEvaluationFixture first = fixture("all-agreed-proposal.json");
        AiEvaluationFixture second = fixture("direct-conflict-proposal.json");
        List<AiEvaluationSample> samples = List.of(
                success(
                        first,
                        AiEvaluationCandidate.empty(),
                        usage(10L, 4L, null, null),
                        AiCostSnapshot.unpriced("trial-unpriced"),
                        7
                ),
                success(
                        second,
                        AiEvaluationCandidate.empty(),
                        usage(null, 6L, null, null),
                        AiCostSnapshot.unpriced("trial-unpriced"),
                        8
                )
        );

        AiEvaluationMetrics metrics = calculator.calculate(List.of(first, second), samples);

        assertThat(metrics.inputTokens().total()).isNull();
        assertThat(metrics.inputTokens().measuredSamples()).isEqualTo(1);
        assertThat(metrics.inputTokens().totalSamples()).isEqualTo(2);
        assertThat(metrics.inputTokens().status())
                .isEqualTo(AiEvaluationMetrics.MeasurementStatus.PARTIALLY_MEASURED);
        assertThat(metrics.outputTokens().total()).isEqualTo(10L);
        assertThat(metrics.outputTokens().status())
                .isEqualTo(AiEvaluationMetrics.MeasurementStatus.MEASURED);
    }

    @Test
    void countsSchemaViolationDetailsWithoutInferringCorrectionExhaustionFromAttempts() {
        AiEvaluationFixture fixture = fixture("all-agreed-proposal.json");
        AiEvaluationSample failed = new AiEvaluationSample(
                fixture.metadata().id(),
                AiEvaluationOutcome.SCHEMA_VALIDATION_FAILURE,
                null,
                usage(3L, 1L, null, null),
                AiCostSnapshot.unpriced("trial-unpriced"),
                2,
                15,
                List.of(
                        SchemaViolationKind.MISSING_REQUIRED_FIELD,
                        SchemaViolationKind.INVALID_ENUM_VALUE
                )
        );

        AiEvaluationMetrics metrics = calculator.calculate(List.of(fixture), List.of(failed));

        assertThat(metrics.schemaValidRate().value()).isZero();
        assertThat(metrics.totalAttemptCount()).isEqualTo(2);
        assertThat(metrics.missingRequiredFieldCount().value()).isEqualTo(1);
        assertThat(metrics.invalidEnumValueCount().value()).isEqualTo(1);
        assertThat(metrics.correctionExhaustedCount().value()).isNull();
        assertThat(metrics.correctionExhaustedCount().measuredSamples()).isZero();
        assertThat(metrics.correctionExhaustedCount().totalSamples()).isEqualTo(1);
        assertThat(metrics.correctionExhaustedCount().status())
                .isEqualTo(AiEvaluationMetrics.MeasurementStatus.NOT_MEASURABLE);
        assertThat(metrics.errorDistribution())
                .containsEntry(AiEvaluationOutcome.SCHEMA_VALIDATION_FAILURE, 1);
    }

    @Test
    void doesNotReportMissingSchemaDetailsAsMeasuredZero() {
        AiEvaluationFixture fixture = fixture("all-agreed-proposal.json");
        AiEvaluationSample failed = new AiEvaluationSample(
                fixture.metadata().id(),
                AiEvaluationOutcome.SCHEMA_VALIDATION_FAILURE,
                null,
                usage(3L, 1L, null, null),
                AiCostSnapshot.unpriced("trial-unpriced"),
                1,
                15,
                List.of()
        );

        AiEvaluationMetrics metrics = calculator.calculate(List.of(fixture), List.of(failed));

        assertThat(metrics.missingRequiredFieldCount().value()).isNull();
        assertThat(metrics.invalidEnumValueCount().status())
                .isEqualTo(AiEvaluationMetrics.MeasurementStatus.NOT_MEASURABLE);
    }

    private AiEvaluationFixture fixture(String name) {
        return loader.load("ai/evaluation/issue-detection/" + name);
    }

    private AiEvaluationSample success(
            AiEvaluationFixture fixture,
            AiEvaluationCandidate candidate,
            AiUsageMetadata usage,
            AiCostSnapshot cost,
            long latency
    ) {
        return new AiEvaluationSample(
                fixture.metadata().id(),
                AiEvaluationOutcome.SUCCESS,
                candidate,
                usage,
                cost,
                1,
                latency,
                List.of()
        );
    }

    private AiUsageMetadata usage(Long input, Long output, Long cacheRead, Long cacheWrite) {
        return new AiUsageMetadata("nvidia", "request", "model", input, output, cacheRead, cacheWrite);
    }

    private AiCostSnapshot priced(String cost) {
        return new AiCostSnapshot(
                "pricing-v1",
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                new BigDecimal(cost)
        );
    }
}
