package com.wevo.backend.ai.evaluation;

import java.math.BigDecimal;
import java.util.Map;

public record AiEvaluationMetrics(
        int fixtureCount,
        int executedCount,
        int totalAttemptCount,
        int qualityFixtureCount,
        RateMetric schemaValidRate,
        CountMetric missingRequiredFieldCount,
        CountMetric invalidEnumValueCount,
        CountMetric correctionExhaustedCount,
        CountMetric unknownEvidenceIdCount,
        RateMetric evidenceCoverage,
        RateMetric issuePrecision,
        RateMetric issueRecall,
        CountMetric mergedConflictCount,
        CountMetric unverifiedFactCount,
        CountMetric forbiddenClaimCount,
        CountMetric missingRequiredFactCount,
        RateMetric reviewGoldRecall,
        RateMetric reviewFalsePositiveRate,
        NullableLongSummary inputTokens,
        NullableLongSummary outputTokens,
        NullableLongSummary cacheReadTokens,
        NullableLongSummary cacheWriteTokens,
        NullableLongSummary reasoningTokens,
        CostSummary cost,
        Long latencyP50Millis,
        Long latencyP95Millis,
        Map<AiEvaluationOutcome, Integer> errorDistribution
) {

    public record RateMetric(
            long numerator,
            long denominator,
            Double value,
            MeasurementStatus status
    ) {
        public static RateMetric of(long numerator, long denominator) {
            return denominator == 0
                    ? new RateMetric(numerator, 0, null, MeasurementStatus.NOT_MEASURABLE)
                    : new RateMetric(
                            numerator,
                            denominator,
                            (double) numerator / denominator,
                            MeasurementStatus.MEASURED
                    );
        }
    }

    public record NullableLongSummary(
            Long total,
            Long p50,
            Long p95,
            int measuredSamples,
            int totalSamples,
            MeasurementStatus status
    ) {
    }

    public record CountMetric(
            Integer value,
            int measuredSamples,
            int totalSamples,
            MeasurementStatus status
    ) {
        public static CountMetric of(int value, int measuredSamples, int totalSamples) {
            if (totalSamples == 0 || measuredSamples == 0) {
                return new CountMetric(null, measuredSamples, totalSamples, MeasurementStatus.NOT_MEASURABLE);
            }
            return new CountMetric(
                    value,
                    measuredSamples,
                    totalSamples,
                    measuredSamples == totalSamples
                            ? MeasurementStatus.MEASURED
                            : MeasurementStatus.PARTIALLY_MEASURED
            );
        }
    }

    public record CostSummary(
            BigDecimal estimatedCost,
            BigDecimal successfulCostP50,
            BigDecimal successfulCostP95,
            CostStatus status,
            int pricedSamples,
            int totalSamples
    ) {
    }

    public enum MeasurementStatus {
        MEASURED,
        PARTIALLY_MEASURED,
        NOT_MEASURABLE
    }

    public enum CostStatus {
        PRICED,
        UNPRICED,
        NOT_MEASURABLE
    }
}
