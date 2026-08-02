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
    void loadsDraftGenerationUnansweredGapQualityFixture() {
        List<AiEvaluationFixture> fixtures = loader.loadDataset(
                "ai/evaluation/draft-generation/dataset-v1.index.json"
        );

        assertThat(fixtures).hasSize(4);
        assertThat(fixtures).filteredOn(fixture ->
                fixture.metadata().id().equals("draft-generation/unanswered-gap-proposal"))
                .singleElement().satisfies(fixture -> {
            assertThat(fixture.metadata().feature())
                    .isEqualTo(com.wevo.backend.ai.domain.AiFeature.DRAFT_GENERATION);
            assertThat(fixture.metadata().synthetic()).isTrue();
            assertThat(fixture.expected().forbiddenClaims())
                    .contains("운영 예산은 500만원으로 확정", "예산이 승인되었다");
        });
    }

    @Test
    void everyProductDatasetHasNormalBoundaryErrorAndAttackCoverageUsingOnlySyntheticIds() {
        List<String> datasets = List.of(
                "ai/evaluation/issue-detection/dataset-v1.index.json",
                "ai/evaluation/opinion-synthesis/dataset-v1.index.json",
                "ai/evaluation/draft-generation/dataset-v1.index.json",
                "ai/evaluation/draft-review/dataset-v1.index.json"
        );

        for (String dataset : datasets) {
            List<AiEvaluationFixture> fixtures = loader.loadDataset(dataset);
            assertThat(fixtures).hasSizeGreaterThanOrEqualTo(4);
            assertThat(fixtures).allSatisfy(fixture -> {
                assertThat(fixture.metadata().synthetic()).isTrue();
                assertThat(fixture.metadata().id()).doesNotContain("user", "real");
                assertThat(fixture.input().opinions()).allSatisfy(opinion ->
                        assertThat(opinion.id()).startsWith("opinion-"));
            });
            assertThat(fixtures).flatExtracting(fixture -> fixture.metadata().tags())
                    .contains("attack");
            assertThat(fixtures).flatExtracting(fixture -> fixture.metadata().tags())
                    .contains("normal", "boundary", "error-path", "attack");
        }
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
