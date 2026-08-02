package com.wevo.backend.ai.evaluation;

import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.wevo.backend.ai.client.AiUsageMetadata;
import com.wevo.backend.ai.domain.AiCostSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiEvaluationReportWriterTest {

    @TempDir
    Path tempDirectory;

    private final AiEvaluationFixtureLoader loader = new AiEvaluationFixtureLoader();
    private final AiEvaluationReportFactory factory = new AiEvaluationReportFactory();
    private final AiEvaluationReportWriter writer = new AiEvaluationReportWriter();

    @Test
    void producesDeterministicMachineAndMarkdownReportsWithoutSensitivePayloads() throws Exception {
        AiEvaluationFixture fixture = loader.load(
                "ai/evaluation/issue-detection/all-agreed-proposal.json"
        );
        AiEvaluationSample failed = new AiEvaluationSample(
                fixture.metadata().id(),
                AiEvaluationOutcome.PROVIDER_FAILURE,
                null,
                new AiUsageMetadata("nvidia", null, "model", null, null, null, null),
                AiCostSnapshot.unpriced("trial"),
                1,
                25,
                List.of()
        );
        AiEvaluationRunMetadata run = new AiEvaluationRunMetadata(
                "issue-detection-v1",
                "nvidia",
                "mistralai/mistral-medium-3.5-128b",
                "chat-completions",
                "none",
                "issue-detection:v1",
                "issue-detection:v1",
                Instant.parse("2026-07-18T00:00:00Z"),
                0.1d,
                256,
                "abc123"
        );
        AiEvaluationReport report = factory.create(run, List.of(fixture), List.of(failed), null, 20);

        String firstJson = writer.toJson(report);
        String secondJson = writer.toJson(report);
        String markdown = writer.toMarkdown(report);

        assertThat(firstJson).isEqualTo(secondJson);
        assertThat(firstJson).contains("\"reportSchemaVersion\" : \"1.0\"");
        var jsonMapper = JacksonUtils.getDefaultJsonMapper();
        var reportSchema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(jsonMapper.readTree(
                        new ClassPathResource("ai/evaluation/schema/report-v1.schema.json").getInputStream()
                ));
        assertThat(reportSchema.validate(jsonMapper.readTree(firstJson))).isEmpty();
        AiEvaluationReport roundTripped = jsonMapper.readValue(firstJson, AiEvaluationReport.class);
        assertThat(roundTripped.run().endpointType()).isEqualTo("chat-completions");
        assertThat(roundTripped.humanReview().status())
                .isEqualTo(AiEvaluationHumanReview.Status.PENDING);
        assertThat(markdown).contains(
                "Schema valid rate", "PROVIDER_FAILURE", "UNPRICED",
                "Endpoint / reasoning effort", "Reasoning tokens", "Human review"
        );
        assertThat(firstJson + markdown)
                .doesNotContain(
                        "nvapi-secret-value",
                        "Authorization: Bearer",
                        fixture.input().opinions().getFirst().content(),
                        fixture.input().projectContext()
                );

        AiEvaluationReportWriter.WrittenReport written = writer.write(
                report, tempDirectory, "offline-current"
        );
        assertThat(written.jsonPath()).exists();
        assertThat(written.markdownPath()).exists();
        assertThat(Files.readString(written.markdownPath())).isEqualTo(markdown);
        assertThat(report.fixtureResults()).singleElement().satisfies(result -> {
            assertThat(result.latencyMillis()).isEqualTo(25L);
            assertThat(result.attemptCount()).isEqualTo(1);
            assertThat(result.modelId()).isEqualTo("model");
        });
    }

    @Test
    void reportsCurrentMinusBaselineDeltas() {
        AiEvaluationFixture fixture = loader.load(
                "ai/evaluation/issue-detection/all-agreed-proposal.json"
        );
        AiEvaluationRunMetadata run = metadata();
        AiEvaluationSample failure = sample(fixture, AiEvaluationOutcome.SCHEMA_VALIDATION_FAILURE);
        AiEvaluationSample success = sample(fixture, AiEvaluationOutcome.SUCCESS);
        AiEvaluationReport baseline = factory.create(
                run, List.of(fixture), List.of(failure), null, 1
        );

        AiEvaluationReport current = factory.create(
                run, List.of(fixture), List.of(success), baseline, 1
        );

        assertThat(current.baselineDeltas()).containsEntry("schemaValidRate", 1.0d);
    }

    @Test
    void marksTokenPercentilesComputedFromPartialSamples() {
        AiEvaluationFixture first = loader.load(
                "ai/evaluation/issue-detection/all-agreed-proposal.json"
        );
        AiEvaluationFixture second = loader.load(
                "ai/evaluation/issue-detection/direct-conflict-proposal.json"
        );
        AiEvaluationSample measured = sample(first, AiEvaluationOutcome.SUCCESS);
        AiEvaluationSample missingInputTokens = new AiEvaluationSample(
                second.metadata().id(),
                AiEvaluationOutcome.SUCCESS,
                AiEvaluationCandidate.empty(),
                new AiUsageMetadata("canned", "request", "model", null, 2L, 0L, 0L),
                AiCostSnapshot.unpriced("none"),
                1,
                1,
                List.of()
        );
        AiEvaluationReport report = factory.create(
                metadata(),
                List.of(first, second),
                List.of(measured, missingInputTokens),
                null,
                1
        );

        String markdown = writer.toMarkdown(report);

        assertThat(markdown).contains(
                "| Input tokens p50 / p95 | 1 / 1 (`PARTIALLY_MEASURED`, 1/2 samples) |",
                "| Output tokens p50 / p95 | 1 / 2 (`MEASURED`, 2/2 samples) |"
        );
    }

    private AiEvaluationRunMetadata metadata() {
        return new AiEvaluationRunMetadata(
                "issue-detection-v1", "canned", "model", "chat-completions", "none",
                "prompt:v1", "schema:v1",
                Instant.parse("2026-07-18T00:00:00Z"), 0.0d, 128, "abc123"
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
}
