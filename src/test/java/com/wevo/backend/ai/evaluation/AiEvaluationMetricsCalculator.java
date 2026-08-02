package com.wevo.backend.ai.evaluation;

import com.wevo.backend.ai.client.AiUsageMetadata;
import com.wevo.backend.ai.domain.AiCostSnapshot;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class AiEvaluationMetricsCalculator {

    public AiEvaluationMetrics calculate(
            List<AiEvaluationFixture> fixtures,
            List<AiEvaluationSample> samples
    ) {
        Map<String, AiEvaluationFixture> fixtureById = new HashMap<>();
        fixtures.forEach(fixture -> fixtureById.put(fixture.metadata().id(), fixture));
        List<AiEvaluationSample> executed = samples.stream()
                .filter(AiEvaluationSample::providerCallStarted)
                .toList();
        List<AiEvaluationSample> successful = executed.stream()
                .filter(sample -> sample.outcome() == AiEvaluationOutcome.SUCCESS)
                .toList();
        int schemaRelevantSamples = (int) executed.stream()
                .filter(sample -> sample.outcome() == AiEvaluationOutcome.SUCCESS
                        || sample.outcome() == AiEvaluationOutcome.SCHEMA_VALIDATION_FAILURE)
                .count();
        int schemaDetailSamples = successful.size() + (int) executed.stream()
                .filter(sample -> sample.outcome() == AiEvaluationOutcome.SCHEMA_VALIDATION_FAILURE)
                .filter(sample -> !sample.schemaViolations().isEmpty())
                .count();

        Counters counters = new Counters();
        for (AiEvaluationSample sample : samples) {
            counters.missingRequiredFields += countViolation(
                    sample, SchemaViolationKind.MISSING_REQUIRED_FIELD
            );
            counters.invalidEnumValues += countViolation(
                    sample, SchemaViolationKind.INVALID_ENUM_VALUE
            );
            if (sample.outcome() != AiEvaluationOutcome.SUCCESS || sample.candidate() == null) {
                continue;
            }
            AiEvaluationFixture fixture = requireFixture(fixtureById, sample.fixtureId());
            accumulateQuality(fixture, sample.candidate(), counters);
        }

        Map<AiEvaluationOutcome, Integer> errors = new EnumMap<>(AiEvaluationOutcome.class);
        samples.stream()
                .filter(sample -> sample.outcome() != AiEvaluationOutcome.SUCCESS)
                .forEach(sample -> errors.merge(sample.outcome(), 1, Integer::sum));
        List<Long> latencies = executed.stream()
                .map(AiEvaluationSample::latencyMillis)
                .sorted()
                .toList();

        return new AiEvaluationMetrics(
                fixtures.size(),
                executed.size(),
                executed.stream().mapToInt(AiEvaluationSample::attemptCount).sum(),
                (int) fixtures.stream()
                        .filter(fixture -> fixture.metadata().kind() == AiEvaluationFixture.Kind.QUALITY)
                        .count(),
                AiEvaluationMetrics.RateMetric.of(successful.size(), executed.size()),
                AiEvaluationMetrics.CountMetric.of(
                        counters.missingRequiredFields, schemaDetailSamples, schemaRelevantSamples
                ),
                AiEvaluationMetrics.CountMetric.of(
                        counters.invalidEnumValues, schemaDetailSamples, schemaRelevantSamples
                ),
                AiEvaluationMetrics.CountMetric.of(0, 0, executed.size()),
                AiEvaluationMetrics.CountMetric.of(
                        counters.unknownEvidenceIds, successful.size(), executed.size()
                ),
                AiEvaluationMetrics.RateMetric.of(counters.coveredClaims, counters.evidenceRequiredClaims),
                AiEvaluationMetrics.RateMetric.of(counters.issueTruePositives, counters.predictedIssues),
                AiEvaluationMetrics.RateMetric.of(counters.issueTruePositives, counters.expectedIssues),
                AiEvaluationMetrics.CountMetric.of(
                        counters.mergedConflicts, successful.size(), executed.size()
                ),
                AiEvaluationMetrics.CountMetric.of(
                        counters.unverifiedFacts, successful.size(), executed.size()
                ),
                AiEvaluationMetrics.CountMetric.of(
                        counters.forbiddenClaims, successful.size(), executed.size()
                ),
                AiEvaluationMetrics.CountMetric.of(
                        counters.missingRequiredFacts, successful.size(), executed.size()
                ),
                AiEvaluationMetrics.RateMetric.of(counters.reviewTruePositives, counters.expectedReviewIssues),
                AiEvaluationMetrics.RateMetric.of(counters.reviewFalsePositives, counters.predictedReviewIssues),
                tokenSummary(executed, AiUsageMetadata::inputTokens),
                tokenSummary(executed, AiUsageMetadata::outputTokens),
                tokenSummary(executed, AiUsageMetadata::cacheReadInputTokens),
                tokenSummary(executed, AiUsageMetadata::cacheWriteInputTokens),
                tokenSummary(executed, AiUsageMetadata::reasoningTokens),
                costSummary(executed, successful),
                percentile(latencies, 0.50d),
                percentile(latencies, 0.95d),
                Map.copyOf(errors)
        );
    }

    private void accumulateQuality(
            AiEvaluationFixture fixture,
            AiEvaluationCandidate candidate,
            Counters counters
    ) {
        Set<String> allowedEvidenceIds = fixture.input().allowedEvidenceIds();
        for (AiEvaluationCandidate.DetectedIssue issue : candidate.issues()) {
            counters.unknownEvidenceIds += issue.evidenceIds().stream()
                    .filter(id -> !allowedEvidenceIds.contains(id))
                    .count();
        }
        for (AiEvaluationCandidate.Claim claim : candidate.claims()) {
            counters.unknownEvidenceIds += claim.evidenceIds().stream()
                    .filter(id -> !allowedEvidenceIds.contains(id))
                    .count();
            if (claim.evidenceRequired()) {
                counters.evidenceRequiredClaims++;
                if (claim.evidenceIds().stream().anyMatch(allowedEvidenceIds::contains)) {
                    counters.coveredClaims++;
                }
            }
        }

        EnumMap<AiEvaluationFixture.IssueType, Integer> expectedTypes = issueCounts(
                fixture.expected().expectedIssues().stream()
                        .map(AiEvaluationFixture.ExpectedIssue::type)
                        .toList()
        );
        EnumMap<AiEvaluationFixture.IssueType, Integer> actualTypes = issueCounts(
                candidate.issues().stream()
                        .map(AiEvaluationCandidate.DetectedIssue::type)
                        .toList()
        );
        counters.expectedIssues += expectedTypes.values().stream().mapToInt(Integer::intValue).sum();
        counters.predictedIssues += actualTypes.values().stream().mapToInt(Integer::intValue).sum();
        for (AiEvaluationFixture.IssueType type : AiEvaluationFixture.IssueType.values()) {
            counters.issueTruePositives += Math.min(
                    expectedTypes.getOrDefault(type, 0),
                    actualTypes.getOrDefault(type, 0)
            );
        }

        counters.mergedConflicts += candidate.mergedConflictCount();
        counters.unverifiedFacts += candidate.unverifiedFactCount();
        counters.forbiddenClaims += forbiddenClaimCount(fixture, candidate);
        counters.missingRequiredFacts += missingRequiredFactCount(fixture, candidate);

        Set<String> goldReviewIds = fixture.expected().goldReviewIssueIds();
        Set<String> actualReviewIds = candidate.reviewIssueIds();
        counters.expectedReviewIssues += goldReviewIds.size();
        counters.predictedReviewIssues += actualReviewIds.size();
        Set<String> intersection = new HashSet<>(actualReviewIds);
        intersection.retainAll(goldReviewIds);
        counters.reviewTruePositives += intersection.size();
        counters.reviewFalsePositives += actualReviewIds.size() - intersection.size();
    }

    private int forbiddenClaimCount(AiEvaluationFixture fixture, AiEvaluationCandidate candidate) {
        int count = 0;
        for (String forbidden : fixture.expected().forbiddenClaims()) {
            String normalizedForbidden = forbidden.toLowerCase(Locale.ROOT);
            for (AiEvaluationCandidate.Claim claim : candidate.claims()) {
                if (claim.text().toLowerCase(Locale.ROOT).contains(normalizedForbidden)) {
                    count++;
                }
            }
        }
        return count;
    }

    private int missingRequiredFactCount(AiEvaluationFixture fixture, AiEvaluationCandidate candidate) {
        int missing = 0;
        for (String requiredFact : fixture.expected().requiredFacts()) {
            String normalizedFact = requiredFact.toLowerCase(Locale.ROOT);
            boolean found = candidate.claims().stream()
                    .map(AiEvaluationCandidate.Claim::text)
                    .map(text -> text.toLowerCase(Locale.ROOT))
                    .anyMatch(text -> text.contains(normalizedFact));
            if (!found) {
                missing++;
            }
        }
        return missing;
    }

    private EnumMap<AiEvaluationFixture.IssueType, Integer> issueCounts(
            List<AiEvaluationFixture.IssueType> types
    ) {
        EnumMap<AiEvaluationFixture.IssueType, Integer> counts =
                new EnumMap<>(AiEvaluationFixture.IssueType.class);
        types.forEach(type -> counts.merge(type, 1, Integer::sum));
        return counts;
    }

    private int countViolation(AiEvaluationSample sample, SchemaViolationKind kind) {
        return (int) sample.schemaViolations().stream().filter(kind::equals).count();
    }

    private AiEvaluationFixture requireFixture(
            Map<String, AiEvaluationFixture> fixtures,
            String fixtureId
    ) {
        AiEvaluationFixture fixture = fixtures.get(fixtureId);
        if (fixture == null) {
            throw new IllegalArgumentException("평가 sample에 대응하는 fixture가 없습니다.");
        }
        return fixture;
    }

    private AiEvaluationMetrics.NullableLongSummary tokenSummary(
            List<AiEvaluationSample> samples,
            Function<AiUsageMetadata, Long> extractor
    ) {
        long total = 0;
        int measured = 0;
        List<Long> measuredValues = new java.util.ArrayList<>();
        for (AiEvaluationSample sample : samples) {
            Long value = sample.usage() == null ? null : extractor.apply(sample.usage());
            if (value != null) {
                total += value;
                measured++;
                measuredValues.add(value);
            }
        }
        boolean complete = !samples.isEmpty() && measured == samples.size();
        AiEvaluationMetrics.MeasurementStatus status;
        if (complete) {
            status = AiEvaluationMetrics.MeasurementStatus.MEASURED;
        } else if (measured > 0) {
            status = AiEvaluationMetrics.MeasurementStatus.PARTIALLY_MEASURED;
        } else {
            status = AiEvaluationMetrics.MeasurementStatus.NOT_MEASURABLE;
        }
        return new AiEvaluationMetrics.NullableLongSummary(
                complete ? total : null,
                percentile(measuredValues.stream().sorted().toList(), 0.50d),
                percentile(measuredValues.stream().sorted().toList(), 0.95d),
                measured,
                samples.size(),
                status
        );
    }

    private AiEvaluationMetrics.CostSummary costSummary(
            List<AiEvaluationSample> samples,
            List<AiEvaluationSample> successfulSamples
    ) {
        BigDecimal total = BigDecimal.ZERO;
        int priced = 0;
        boolean unpriced = false;
        boolean missing = false;
        for (AiEvaluationSample sample : samples) {
            AiCostSnapshot cost = sample.cost();
            if (cost == null) {
                missing = true;
            } else if (cost.estimatedCost() == null) {
                unpriced = true;
            } else {
                total = total.add(cost.estimatedCost());
                priced++;
            }
        }
        if (samples.isEmpty() || missing) {
            return new AiEvaluationMetrics.CostSummary(
                    null, null, null,
                    AiEvaluationMetrics.CostStatus.NOT_MEASURABLE, priced, samples.size()
            );
        }
        if (unpriced) {
            return new AiEvaluationMetrics.CostSummary(
                    null, null, null,
                    AiEvaluationMetrics.CostStatus.UNPRICED, priced, samples.size()
            );
        }
        List<BigDecimal> successfulCosts = successfulSamples.stream()
                .map(AiEvaluationSample::cost)
                .filter(java.util.Objects::nonNull)
                .map(AiCostSnapshot::estimatedCost)
                .filter(java.util.Objects::nonNull)
                .sorted()
                .toList();
        boolean successfulCostComplete = !successfulSamples.isEmpty()
                && successfulCosts.size() == successfulSamples.size();
        return new AiEvaluationMetrics.CostSummary(
                total,
                successfulCostComplete ? percentileDecimal(successfulCosts, 0.50d) : null,
                successfulCostComplete ? percentileDecimal(successfulCosts, 0.95d) : null,
                AiEvaluationMetrics.CostStatus.PRICED, priced, samples.size()
        );
    }

    private BigDecimal percentileDecimal(List<BigDecimal> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) {
            return null;
        }
        int rank = (int) Math.ceil(percentile * sortedValues.size());
        return sortedValues.get(Math.max(0, rank - 1));
    }

    private Long percentile(List<Long> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) {
            return null;
        }
        int rank = (int) Math.ceil(percentile * sortedValues.size());
        return sortedValues.get(Math.max(0, rank - 1));
    }

    private static final class Counters {
        private int missingRequiredFields;
        private int invalidEnumValues;
        private int unknownEvidenceIds;
        private int coveredClaims;
        private int evidenceRequiredClaims;
        private int issueTruePositives;
        private int predictedIssues;
        private int expectedIssues;
        private int mergedConflicts;
        private int unverifiedFacts;
        private int forbiddenClaims;
        private int missingRequiredFacts;
        private int reviewTruePositives;
        private int reviewFalsePositives;
        private int expectedReviewIssues;
        private int predictedReviewIssues;
    }
}
