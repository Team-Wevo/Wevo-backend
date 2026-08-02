package com.wevo.backend.ai.evaluation;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class AiEvaluationReportFactory {

    public static final String REPORT_SCHEMA_VERSION = "1.0";

    private final AiEvaluationMetricsCalculator metricsCalculator;
    private final AiEvaluationGate gate;

    public AiEvaluationReportFactory() {
        this(new AiEvaluationMetricsCalculator(), new AiEvaluationGate());
    }

    AiEvaluationReportFactory(
            AiEvaluationMetricsCalculator metricsCalculator,
            AiEvaluationGate gate
    ) {
        this.metricsCalculator = metricsCalculator;
        this.gate = gate;
    }

    public AiEvaluationReport create(
            AiEvaluationRunMetadata run,
            List<AiEvaluationFixture> fixtures,
            List<AiEvaluationSample> samples,
            AiEvaluationReport baseline,
            int minimumSamples
    ) {
        return create(
                run, fixtures, samples, baseline, minimumSamples,
                AiEvaluationHumanReview.pending()
        );
    }

    public AiEvaluationReport create(
            AiEvaluationRunMetadata run,
            List<AiEvaluationFixture> fixtures,
            List<AiEvaluationSample> samples,
            AiEvaluationReport baseline,
            int minimumSamples,
            AiEvaluationHumanReview humanReview
    ) {
        validateDatasetVersion(run, fixtures);
        validateBaselineCompatibility(run, fixtures, baseline);
        validateHumanReview(fixtures, humanReview);
        AiEvaluationMetrics metrics = metricsCalculator.calculate(fixtures, samples);
        List<AiEvaluationReport.Failure> failures = samples.stream()
                .filter(sample -> sample.outcome() != AiEvaluationOutcome.SUCCESS)
                .map(sample -> new AiEvaluationReport.Failure(sample.fixtureId(), sample.outcome()))
                .sorted(Comparator.comparing(AiEvaluationReport.Failure::fixtureId))
                .toList();
        List<AiEvaluationReport.FixtureResult> fixtureResults = samples.stream()
                .map(this::fixtureResult)
                .sorted(Comparator.comparing(AiEvaluationReport.FixtureResult::fixtureId))
                .toList();
        return new AiEvaluationReport(
                REPORT_SCHEMA_VERSION,
                run,
                metrics,
                gate.evaluate(metrics, minimumSamples),
                humanReview,
                deltas(metrics, baseline),
                fixtureResults,
                failures
        );
    }

    private void validateHumanReview(
            List<AiEvaluationFixture> fixtures,
            AiEvaluationHumanReview humanReview
    ) {
        if (humanReview == null) {
            throw new IllegalArgumentException("사람 평가 결과는 필수입니다.");
        }
        Set<String> qualityFixtureIds = fixtures.stream()
                .filter(fixture -> fixture.metadata().kind() == AiEvaluationFixture.Kind.QUALITY)
                .map(fixture -> fixture.metadata().id())
                .collect(Collectors.toUnmodifiableSet());
        if (!qualityFixtureIds.containsAll(humanReview.fixtureScores().keySet())) {
            throw new IllegalArgumentException("사람 평가는 현재 QUALITY fixture에만 기록할 수 있습니다.");
        }
        if (humanReview.status() == AiEvaluationHumanReview.Status.COMPLETED
                && !humanReview.fixtureScores().keySet().equals(qualityFixtureIds)) {
            throw new IllegalArgumentException("완료된 사람 평가는 모든 QUALITY fixture 점수가 필요합니다.");
        }
    }

    private AiEvaluationReport.FixtureResult fixtureResult(AiEvaluationSample sample) {
        var usage = sample.usage();
        var cost = sample.cost();
        AiEvaluationMetrics.CostStatus costStatus;
        if (cost == null) {
            costStatus = AiEvaluationMetrics.CostStatus.NOT_MEASURABLE;
        } else if (cost.estimatedCost() == null) {
            costStatus = AiEvaluationMetrics.CostStatus.UNPRICED;
        } else {
            costStatus = AiEvaluationMetrics.CostStatus.PRICED;
        }
        return new AiEvaluationReport.FixtureResult(
                sample.fixtureId(),
                sample.outcome(),
                sample.latencyMillis(),
                sample.attemptCount(),
                usage == null ? null : usage.providerId(),
                usage == null ? null : usage.providerRequestId(),
                usage == null ? null : usage.modelId(),
                usage == null ? null : usage.inputTokens(),
                usage == null ? null : usage.outputTokens(),
                usage == null ? null : usage.cacheReadInputTokens(),
                usage == null ? null : usage.cacheWriteInputTokens(),
                usage == null ? null : usage.reasoningTokens(),
                cost == null ? null : cost.estimatedCost(),
                costStatus
        );
    }

    private void validateDatasetVersion(
            AiEvaluationRunMetadata run,
            List<AiEvaluationFixture> fixtures
    ) {
        boolean mismatch = fixtures.stream()
                .anyMatch(fixture -> !run.datasetVersion().equals(fixture.metadata().datasetVersion()));
        if (mismatch) {
            throw new IllegalArgumentException("run metadata와 fixture dataset version이 일치하지 않습니다.");
        }
    }

    private void validateBaselineCompatibility(
            AiEvaluationRunMetadata run,
            List<AiEvaluationFixture> fixtures,
            AiEvaluationReport baseline
    ) {
        if (baseline == null) {
            return;
        }
        if (baseline.run() == null
                || baseline.metrics() == null
                || baseline.fixtureResults() == null) {
            throw incompatibleBaseline();
        }

        Set<String> currentFixtureIds = fixtures.stream()
                .map(fixture -> fixture.metadata().id())
                .collect(Collectors.toUnmodifiableSet());
        Set<String> baselineFixtureIds = baseline.fixtureResults().stream()
                .map(AiEvaluationReport.FixtureResult::fixtureId)
                .collect(Collectors.toUnmodifiableSet());
        boolean compatible = REPORT_SCHEMA_VERSION.equals(baseline.reportSchemaVersion())
                && run.datasetVersion().equals(baseline.run().datasetVersion())
                && run.schemaVersion().equals(baseline.run().schemaVersion())
                && fixtures.size() == baseline.metrics().fixtureCount()
                && fixtures.size() == baseline.fixtureResults().size()
                && currentFixtureIds.equals(baselineFixtureIds);
        if (!compatible) {
            throw incompatibleBaseline();
        }
    }

    private IllegalArgumentException incompatibleBaseline() {
        return new IllegalArgumentException("호환되지 않는 baseline report입니다.");
    }

    private Map<String, Double> deltas(
            AiEvaluationMetrics current,
            AiEvaluationReport baseline
    ) {
        if (baseline == null) {
            return Map.of();
        }
        AiEvaluationMetrics previous = baseline.metrics();
        Map<String, Double> deltas = new TreeMap<>();
        putRateDelta(deltas, "schemaValidRate", current.schemaValidRate(), previous.schemaValidRate());
        putRateDelta(deltas, "evidenceCoverage", current.evidenceCoverage(), previous.evidenceCoverage());
        putRateDelta(deltas, "issuePrecision", current.issuePrecision(), previous.issuePrecision());
        putRateDelta(deltas, "issueRecall", current.issueRecall(), previous.issueRecall());
        putCountDelta(
                deltas,
                "unknownEvidenceIdCount",
                current.unknownEvidenceIdCount(),
                previous.unknownEvidenceIdCount()
        );
        putCountDelta(
                deltas,
                "unverifiedFactCount",
                current.unverifiedFactCount(),
                previous.unverifiedFactCount()
        );
        putLongDelta(deltas, "latencyP50Millis", current.latencyP50Millis(), previous.latencyP50Millis());
        putLongDelta(deltas, "latencyP95Millis", current.latencyP95Millis(), previous.latencyP95Millis());
        return Map.copyOf(deltas);
    }

    private void putCountDelta(
            Map<String, Double> deltas,
            String name,
            AiEvaluationMetrics.CountMetric current,
            AiEvaluationMetrics.CountMetric previous
    ) {
        if (current.value() != null && previous.value() != null) {
            deltas.put(name, (double) current.value() - previous.value());
        }
    }

    private void putRateDelta(
            Map<String, Double> deltas,
            String name,
            AiEvaluationMetrics.RateMetric current,
            AiEvaluationMetrics.RateMetric previous
    ) {
        if (current.value() != null && previous.value() != null) {
            deltas.put(name, current.value() - previous.value());
        }
    }

    private void putLongDelta(
            Map<String, Double> deltas,
            String name,
            Long current,
            Long previous
    ) {
        if (current != null && previous != null) {
            deltas.put(name, (double) current - previous);
        }
    }
}
