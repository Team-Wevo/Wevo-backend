package com.wevo.backend.ai.evaluation;

import com.wevo.backend.project.domain.OutputType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiEvaluationFixtureLoaderTest {

    private final AiEvaluationFixtureLoader loader = new AiEvaluationFixtureLoader();

    @Test
    void loadsVersionedSyntheticDatasetWithAllInitialScenarios() {
        List<AiEvaluationFixture> fixtures = loader.loadDataset(
                "ai/evaluation/issue-detection/dataset-v1.index.json"
        );

        assertThat(fixtures).hasSize(11);
        assertThat(fixtures).allSatisfy(fixture -> {
            assertThat(fixture.schemaVersion()).isEqualTo(1);
            assertThat(fixture.metadata().synthetic()).isTrue();
            assertThat(fixture.metadata().datasetVersion()).isEqualTo("issue-detection-v1");
        });
        assertThat(fixtures).extracting(fixture -> fixture.input().outputType())
                .contains(OutputType.PROPOSAL, OutputType.PRESENTATION);
        assertThat(fixtures).extracting(fixture -> fixture.metadata().kind())
                .contains(AiEvaluationFixture.Kind.CONTRACT, AiEvaluationFixture.Kind.QUALITY);
        assertThat(fixtures).flatExtracting(fixture -> fixture.metadata().tags())
                .contains(
                        "agreement",
                        "direct-conflict",
                        "implicit-conflict",
                        "gap",
                        "minority",
                        "excluded-input",
                        "long-input",
                        "evidence-allowlist",
                        "prompt-injection",
                        "unverified-fact"
                );
    }

    @Test
    void excludesDeletedAndUnsubmittedOpinionsFromEvidenceAllowlist() {
        AiEvaluationFixture fixture = loader.load(
                "ai/evaluation/issue-detection/excluded-opinions-proposal.json"
        );

        assertThat(fixture.input().allowedEvidenceIds()).containsExactly("opinion-active");
    }

    @Test
    void rejectsMissingRequiredValueAndUnknownEnumBeforeRecordConversion() {
        assertThatThrownBy(() -> loader.load("ai/evaluation/invalid/missing-language.json"))
                .isInstanceOf(AiEvaluationFixtureException.class)
                .hasMessageContaining("schema 검증");
        assertThatThrownBy(() -> loader.load("ai/evaluation/invalid/invalid-enum.json"))
                .isInstanceOf(AiEvaluationFixtureException.class)
                .hasMessageContaining("schema 검증");
    }

    @Test
    void rejectsMalformedJsonSchemaAtLoaderConstruction() {
        assertThatThrownBy(() -> new AiEvaluationFixtureLoader(
                "ai/evaluation/invalid/invalid-schema.json"
        ))
                .isInstanceOf(AiEvaluationFixtureException.class)
                .hasMessageContaining("JSON Schema");
    }
}
