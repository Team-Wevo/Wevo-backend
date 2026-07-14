package com.wevo.backend.ai.service;

import com.wevo.backend.ai.domain.AiFeature;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiJobIdempotencyKeyGeneratorTest {

    private static final String SNAPSHOT = "a".repeat(64);

    private final AiJobIdempotencyKeyGenerator generator = new AiJobIdempotencyKeyGenerator();

    @Test
    void generatesDeterministicSha256WithoutExposingCanonicalValues() {
        AiJobIdempotencyInput input = input(
                AiFeature.OPINION_SYNTHESIS, 1L, 2L, SNAPSHOT,
                "의견-버전:3", "opinion-synthesis:v1", "opinion-synthesis:v2",
                "claude-model", 4096
        );

        String first = generator.generate(input);
        String second = generator.generate(input);

        assertThat(first).isEqualTo(second).matches("[0-9a-f]{64}");
        assertThat(first)
                .doesNotContain("의견")
                .doesNotContain("claude")
                .doesNotContain("OPINION_SYNTHESIS");
    }

    @Test
    void everyOutputIdentityComponentChangesTheKey() {
        AiJobIdempotencyInput base = input(
                AiFeature.OPINION_SYNTHESIS, 1L, 2L, SNAPSHOT,
                "source:v1", "prompt:v1", "schema:v1", "model-a", 4096
        );
        String baseKey = generator.generate(base);

        assertThat(generator.generate(input(
                AiFeature.ISSUE_DETECTION, 1L, 2L, SNAPSHOT,
                "source:v1", "prompt:v1", "schema:v1", "model-a", 4096
        ))).isNotEqualTo(baseKey);
        assertThat(generator.generate(input(
                AiFeature.OPINION_SYNTHESIS, 9L, 2L, SNAPSHOT,
                "source:v1", "prompt:v1", "schema:v1", "model-a", 4096
        ))).isNotEqualTo(baseKey);
        assertThat(generator.generate(input(
                AiFeature.OPINION_SYNTHESIS, 1L, 3L, SNAPSHOT,
                "source:v1", "prompt:v1", "schema:v1", "model-a", 4096
        ))).isNotEqualTo(baseKey);
        assertThat(generator.generate(input(
                AiFeature.OPINION_SYNTHESIS, 1L, 2L, "b".repeat(64),
                "source:v1", "prompt:v1", "schema:v1", "model-a", 4096
        ))).isNotEqualTo(baseKey);
        assertThat(generator.generate(input(
                AiFeature.OPINION_SYNTHESIS, 1L, 2L, SNAPSHOT,
                "source:v2", "prompt:v1", "schema:v1", "model-a", 4096
        ))).isNotEqualTo(baseKey);
        assertThat(generator.generate(input(
                AiFeature.OPINION_SYNTHESIS, 1L, 2L, SNAPSHOT,
                "source:v1", "prompt:v2", "schema:v1", "model-a", 4096
        ))).isNotEqualTo(baseKey);
        assertThat(generator.generate(input(
                AiFeature.OPINION_SYNTHESIS, 1L, 2L, SNAPSHOT,
                "source:v1", "prompt:v1", "schema:v2", "model-a", 4096
        ))).isNotEqualTo(baseKey);
        assertThat(generator.generate(input(
                AiFeature.OPINION_SYNTHESIS, 1L, 2L, SNAPSHOT,
                "source:v1", "prompt:v1", "schema:v1", "model-b", 4096
        ))).isNotEqualTo(baseKey);
        assertThat(generator.generate(input(
                AiFeature.OPINION_SYNTHESIS, 1L, 2L, SNAPSHOT,
                "source:v1", "prompt:v1", "schema:v1", "model-a", 2048
        ))).isNotEqualTo(baseKey);
    }

    @Test
    void nullSectionHasAStableDistinctRepresentation() {
        String projectJob = generator.generate(input(
                AiFeature.DRAFT_GENERATION, 1L, null, SNAPSHOT,
                "source:v1", "prompt:v1", "schema:v1", "model", 100
        ));
        String sectionJob = generator.generate(input(
                AiFeature.DRAFT_GENERATION, 1L, 1L, SNAPSHOT,
                "source:v1", "prompt:v1", "schema:v1", "model", 100
        ));

        assertThat(projectJob).isNotEqualTo(sectionJob);
    }

    @Test
    void rejectsMissingOrMalformedCanonicalValues() {
        assertThatThrownBy(() -> input(
                AiFeature.DRAFT_GENERATION, 1L, 2L, "bad-hash",
                "source:v1", "prompt:v1", "schema:v1", "model", 100
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> input(
                AiFeature.DRAFT_GENERATION, 1L, 2L, SNAPSHOT,
                "source:v1", "prompt:v1", " ", "model", 100
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generator.generate(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private AiJobIdempotencyInput input(
            AiFeature feature,
            Long projectId,
            Long sectionId,
            String snapshot,
            String sourceVersion,
            String promptVersion,
            String schemaVersion,
            String modelId,
            int maxOutputTokens
    ) {
        return new AiJobIdempotencyInput(
                feature, projectId, sectionId, snapshot, sourceVersion,
                promptVersion, schemaVersion, modelId, maxOutputTokens
        );
    }
}
