package com.wevo.backend.ai.evaluation;

import com.wevo.backend.ai.client.AiUsageMetadata;
import com.wevo.backend.ai.domain.AiCostSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiEvaluationReportFactoryTest {

    private static final String INCOMPATIBLE_BASELINE_MESSAGE = "호환되지 않는 baseline report입니다.";

    private final AiEvaluationFixtureLoader loader = new AiEvaluationFixtureLoader();
    private final AiEvaluationReportFactory factory = new AiEvaluationReportFactory();

    @Test
    void calculatesDeltasForCompatibleBaseline() {
        AiEvaluationFixture fixture = fixture("all-agreed-proposal.json");
        AiEvaluationRunMetadata run = metadata(fixture.metadata().datasetVersion(), "issue-detection:v1");
        AiEvaluationReport baseline = report(run, List.of(fixture), AiEvaluationOutcome.SCHEMA_VALIDATION_FAILURE);

        AiEvaluationReport current = factory.create(
                run,
                List.of(fixture),
                List.of(sample(fixture, AiEvaluationOutcome.SUCCESS)),
                baseline,
                1
        );

        assertThat(current.baselineDeltas()).containsEntry("schemaValidRate", 1.0d);
    }

    @Test
    void rejectsBaselineWithDifferentReportSchemaVersion() {
        AiEvaluationFixture fixture = fixture("all-agreed-proposal.json");
        AiEvaluationRunMetadata run = metadata(fixture.metadata().datasetVersion(), "issue-detection:v1");
        AiEvaluationReport baseline = report(run, List.of(fixture), AiEvaluationOutcome.SUCCESS);
        AiEvaluationReport incompatible = new AiEvaluationReport(
                "2.0",
                baseline.run(),
                baseline.metrics(),
                baseline.gate(),
                baseline.humanReview(),
                baseline.baselineDeltas(),
                baseline.fixtureResults(),
                baseline.failures()
        );

        assertIncompatible(run, List.of(fixture), List.of(sample(fixture, AiEvaluationOutcome.SUCCESS)), incompatible);
    }

    @Test
    void rejectsBaselineWithDifferentDatasetVersion() {
        AiEvaluationFixture baselineFixture = fixture("all-agreed-proposal.json");
        AiEvaluationRunMetadata baselineRun = metadata(
                baselineFixture.metadata().datasetVersion(), "issue-detection:v1"
        );
        AiEvaluationReport baseline = report(
                baselineRun, List.of(baselineFixture), AiEvaluationOutcome.SUCCESS
        );
        AiEvaluationFixture currentFixture = withDatasetVersion(baselineFixture, "issue-detection-v2");
        AiEvaluationRunMetadata currentRun = metadata("issue-detection-v2", "issue-detection:v1");

        assertIncompatible(
                currentRun,
                List.of(currentFixture),
                List.of(sample(currentFixture, AiEvaluationOutcome.SUCCESS)),
                baseline
        );
    }

    @Test
    void rejectsBaselineWithDifferentOutputSchemaVersion() {
        AiEvaluationFixture fixture = fixture("all-agreed-proposal.json");
        AiEvaluationRunMetadata baselineRun = metadata(
                fixture.metadata().datasetVersion(), "issue-detection:v1"
        );
        AiEvaluationReport baseline = report(baselineRun, List.of(fixture), AiEvaluationOutcome.SUCCESS);
        AiEvaluationRunMetadata currentRun = metadata(
                fixture.metadata().datasetVersion(), "issue-detection:v2"
        );

        assertIncompatible(
                currentRun,
                List.of(fixture),
                List.of(sample(fixture, AiEvaluationOutcome.SUCCESS)),
                baseline
        );
    }

    @Test
    void rejectsBaselineWithDifferentFixtureIdsEvenWhenCountMatches() {
        AiEvaluationFixture baselineFixture = fixture("all-agreed-proposal.json");
        AiEvaluationFixture currentFixture = fixture("direct-conflict-proposal.json");
        AiEvaluationRunMetadata run = metadata(
                baselineFixture.metadata().datasetVersion(), "issue-detection:v1"
        );
        AiEvaluationReport baseline = report(run, List.of(baselineFixture), AiEvaluationOutcome.SUCCESS);

        assertIncompatible(
                run,
                List.of(currentFixture),
                List.of(sample(currentFixture, AiEvaluationOutcome.SUCCESS)),
                baseline
        );
    }

    @Test
    void rejectsBaselineWithDifferentFixtureCount() {
        AiEvaluationFixture first = fixture("all-agreed-proposal.json");
        AiEvaluationFixture second = fixture("direct-conflict-proposal.json");
        AiEvaluationRunMetadata run = metadata(first.metadata().datasetVersion(), "issue-detection:v1");
        AiEvaluationReport baseline = report(run, List.of(first), AiEvaluationOutcome.SUCCESS);

        assertIncompatible(
                run,
                List.of(first, second),
                List.of(
                        sample(first, AiEvaluationOutcome.SUCCESS),
                        sample(second, AiEvaluationOutcome.SUCCESS)
                ),
                baseline
        );
    }

    @Test
    void keepsPendingHumanReviewSeparateAndRequiresEveryQualityScoreBeforeCompletion() {
        AiEvaluationFixture fixture = fixture("all-agreed-proposal.json");
        AiEvaluationRunMetadata run = metadata(
                fixture.metadata().datasetVersion(), "issue-detection:v1"
        );
        AiEvaluationReport pending = factory.create(
                run,
                List.of(fixture),
                List.of(sample(fixture, AiEvaluationOutcome.SUCCESS)),
                null,
                1
        );

        assertThat(pending.humanReview().status())
                .isEqualTo(AiEvaluationHumanReview.Status.PENDING);
        assertThatThrownBy(() -> factory.create(
                run,
                List.of(fixture),
                List.of(sample(fixture, AiEvaluationOutcome.SUCCESS)),
                null,
                1,
                new AiEvaluationHumanReview(
                        AiEvaluationHumanReview.Status.COMPLETED,
                        AiEvaluationHumanReview.ReviewerAlias.AI_OWNER,
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Map.of()
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("모든 QUALITY fixture");
    }

    private void assertIncompatible(
            AiEvaluationRunMetadata run,
            List<AiEvaluationFixture> fixtures,
            List<AiEvaluationSample> samples,
            AiEvaluationReport baseline
    ) {
        assertThatThrownBy(() -> factory.create(run, fixtures, samples, baseline, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(INCOMPATIBLE_BASELINE_MESSAGE);
    }

    private AiEvaluationReport report(
            AiEvaluationRunMetadata run,
            List<AiEvaluationFixture> fixtures,
            AiEvaluationOutcome outcome
    ) {
        List<AiEvaluationSample> samples = fixtures.stream()
                .map(fixture -> sample(fixture, outcome))
                .toList();
        return factory.create(run, fixtures, samples, null, 1);
    }

    private AiEvaluationRunMetadata metadata(String datasetVersion, String schemaVersion) {
        return new AiEvaluationRunMetadata(
                datasetVersion,
                "canned",
                "model",
                "chat-completions",
                "none",
                "issue-detection:v1",
                schemaVersion,
                Instant.parse("2026-07-18T00:00:00Z"),
                0.0d,
                128,
                "abc123"
        );
    }

    private AiEvaluationSample sample(
            AiEvaluationFixture fixture,
            AiEvaluationOutcome outcome
    ) {
        return new AiEvaluationSample(
                fixture.metadata().id(),
                outcome,
                outcome == AiEvaluationOutcome.SUCCESS ? AiEvaluationCandidate.empty() : null,
                new AiUsageMetadata("canned", "request", "model", 1L, 1L, 0L, 0L),
                AiCostSnapshot.unpriced("none"),
                1,
                1,
                List.of()
        );
    }

    private AiEvaluationFixture fixture(String name) {
        return loader.load("ai/evaluation/issue-detection/" + name);
    }

    private AiEvaluationFixture withDatasetVersion(
            AiEvaluationFixture fixture,
            String datasetVersion
    ) {
        AiEvaluationFixture.Metadata metadata = fixture.metadata();
        return new AiEvaluationFixture(
                fixture.schemaVersion(),
                new AiEvaluationFixture.Metadata(
                        metadata.id(),
                        metadata.feature(),
                        metadata.description(),
                        metadata.language(),
                        metadata.tags(),
                        datasetVersion,
                        metadata.kind(),
                        metadata.synthetic()
                ),
                fixture.input(),
                fixture.expected()
        );
    }
}
