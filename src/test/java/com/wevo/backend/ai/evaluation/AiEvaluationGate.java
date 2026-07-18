package com.wevo.backend.ai.evaluation;

import java.util.ArrayList;
import java.util.List;

public final class AiEvaluationGate {

    public static final double MIN_SCHEMA_VALID_RATE = 0.99d;
    public static final double MIN_EVIDENCE_COVERAGE = 1.0d;
    public static final int DEFAULT_MINIMUM_SAMPLES = 20;

    public Result evaluate(AiEvaluationMetrics metrics, int minimumSamples) {
        if (minimumSamples <= 0) {
            throw new IllegalArgumentException("품질 gate 최소 표본 수는 0보다 커야 합니다.");
        }
        List<String> reasons = new ArrayList<>();
        boolean integrityFailed = false;
        if (positive(metrics.unknownEvidenceIdCount())) {
            reasons.add("UNKNOWN_EVIDENCE_ID");
            integrityFailed = true;
        }
        if (positive(metrics.unverifiedFactCount()) || positive(metrics.forbiddenClaimCount())) {
            reasons.add("UNVERIFIED_OR_FORBIDDEN_CLAIM");
            integrityFailed = true;
        }

        AutomaticStatus automaticStatus;
        if (integrityFailed) {
            automaticStatus = AutomaticStatus.FAIL;
        } else if (metrics.executedCount() < minimumSamples) {
            automaticStatus = AutomaticStatus.INSUFFICIENT_SAMPLES;
            reasons.add("INSUFFICIENT_SAMPLES");
        } else if (below(metrics.schemaValidRate(), MIN_SCHEMA_VALID_RATE)
                || below(metrics.evidenceCoverage(), MIN_EVIDENCE_COVERAGE)) {
            automaticStatus = AutomaticStatus.FAIL;
            reasons.add("QUALITY_THRESHOLD_NOT_MET");
        } else {
            automaticStatus = AutomaticStatus.PASS;
        }

        OverallStatus overallStatus;
        if (automaticStatus == AutomaticStatus.FAIL) {
            overallStatus = OverallStatus.FAIL;
        } else if (automaticStatus == AutomaticStatus.INSUFFICIENT_SAMPLES
                || metrics.qualityFixtureCount() > 0) {
            overallStatus = OverallStatus.REVIEW_REQUIRED;
            if (metrics.qualityFixtureCount() > 0) {
                reasons.add("HUMAN_REVIEW_REQUIRED");
            }
        } else {
            overallStatus = OverallStatus.PASS;
        }
        return new Result(automaticStatus, overallStatus, List.copyOf(reasons));
    }

    private boolean below(AiEvaluationMetrics.RateMetric metric, double threshold) {
        return metric.status() == AiEvaluationMetrics.MeasurementStatus.NOT_MEASURABLE
                || metric.value() < threshold;
    }

    private boolean positive(AiEvaluationMetrics.CountMetric metric) {
        return metric.value() != null && metric.value() > 0;
    }

    public record Result(
            AutomaticStatus automaticStatus,
            OverallStatus overallStatus,
            List<String> reasons
    ) {
    }

    public enum AutomaticStatus {
        PASS,
        FAIL,
        INSUFFICIENT_SAMPLES
    }

    public enum OverallStatus {
        PASS,
        FAIL,
        REVIEW_REQUIRED
    }
}
