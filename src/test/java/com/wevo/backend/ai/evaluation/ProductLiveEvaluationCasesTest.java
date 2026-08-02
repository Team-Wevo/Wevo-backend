package com.wevo.backend.ai.evaluation;

import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.wevo.backend.ai.client.AiUsageMetadata;
import com.wevo.backend.ai.client.StructuredAiProviderRequest;
import com.wevo.backend.ai.client.StructuredOutputDefinition;
import com.wevo.backend.ai.client.StructuredOutputSemanticException;
import com.wevo.backend.ai.domain.AiCostSnapshot;
import com.wevo.backend.ai.domain.AiSectionFindingType;
import com.wevo.backend.ai.dto.model.DraftReviewOutput;
import com.wevo.backend.ai.dto.model.DraftReviewRewriteOutput;
import com.wevo.backend.ai.dto.model.IssueDetectionIssueOutput;
import com.wevo.backend.ai.dto.model.IssueDetectionOutput;
import com.wevo.backend.ai.prompt.DraftGenerationPromptFactory;
import com.wevo.backend.ai.prompt.DraftReviewPromptFactory;
import com.wevo.backend.ai.prompt.IssueDetectionPromptFactory;
import com.wevo.backend.ai.prompt.SynthesisPromptFactory;
import com.wevo.backend.ai.service.DraftGenerationOutput;
import com.wevo.backend.ai.service.SynthesisAiOutput;
import com.wevo.backend.issue.domain.IssueType;
import org.junit.jupiter.api.Test;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "wevo.ai.provider=none")
class ProductLiveEvaluationCasesTest {

    @Autowired
    private IssueDetectionPromptFactory issueDetectionPromptFactory;

    @Autowired
    private SynthesisPromptFactory synthesisPromptFactory;

    @Autowired
    private DraftGenerationPromptFactory draftGenerationPromptFactory;

    @Autowired
    private DraftReviewPromptFactory draftReviewPromptFactory;

    private final AiEvaluationFixtureLoader loader = new AiEvaluationFixtureLoader();

    @Test
    void usesActualPromptAndOutputContractsForAllFourFeatures() {
        ProductLiveEvaluationCases cases = cases();

        StructuredAiProviderRequest<IssueDetectionOutput> issue = cases.issueDetection()
                .requestFor(fixture("issue-detection", "direct-conflict-proposal.json"));
        StructuredAiProviderRequest<SynthesisAiOutput> synthesis = cases.opinionSynthesis()
                .requestFor(fixture("opinion-synthesis", "consensus-proposal.json"));
        StructuredAiProviderRequest<DraftGenerationOutput> draft = cases.draftGeneration()
                .requestFor(fixture("draft-generation", "unanswered-gap-proposal.json"));
        StructuredAiProviderRequest<DraftReviewOutput> review = cases.draftReview()
                .requestFor(fixture("draft-review", "no-findings-proposal.json"));

        assertThat(issue.prompt().id()).isEqualTo(IssueDetectionPromptFactory.PROMPT_ID);
        assertThat(issue.outputDefinition().schemaId())
                .isEqualTo(com.wevo.backend.ai.service.IssueDetectionOutputDefinition.SCHEMA_ID);
        assertThat(synthesis.prompt().id()).isEqualTo(SynthesisPromptFactory.PROMPT_ID);
        assertThat(synthesis.outputDefinition().schemaId())
                .isEqualTo(com.wevo.backend.ai.service.SynthesisOutputDefinition.SCHEMA_ID);
        assertThat(draft.prompt().id()).isEqualTo(DraftGenerationPromptFactory.PROMPT_ID);
        assertThat(draft.outputDefinition().schemaId())
                .isEqualTo(com.wevo.backend.ai.service.DraftGenerationOutputDefinition.SCHEMA_ID);
        assertThat(review.prompt().id()).isEqualTo(DraftReviewPromptFactory.PROMPT_ID);
        assertThat(review.outputDefinition().schemaId())
                .isEqualTo(com.wevo.backend.ai.service.DraftReviewOutputDefinition.SCHEMA_ID);
    }

    @Test
    void actualSchemasRejectMissingValuesWrongTypesAndInvalidEnumsPerFeature() {
        ProductLiveEvaluationCases cases = cases();
        assertSchemaRejected(
                cases.issueDetection().requestFor(
                        fixture("issue-detection", "direct-conflict-proposal.json"))
                        .outputDefinition(),
                """
                        {"issues":[{"type":"UNKNOWN","description":"x",
                        "evidenceOpinionIds":[10001],"question":"q","options":["a","b"]}]}
                        """
        );
        assertSchemaRejected(
                cases.opinionSynthesis().requestFor(
                        fixture("opinion-synthesis", "consensus-proposal.json"))
                        .outputDefinition(),
                "{}"
        );
        assertSchemaRejected(
                cases.draftGeneration().requestFor(
                        fixture("draft-generation", "stale-input-boundary.json"))
                        .outputDefinition(),
                """
                        {"content":"x","evidenceOpinionIds":["not-an-id"],
                        "evidenceIssueIds":[],"evidenceAnswerIds":[],"unresolvedGapIssueIds":[]}
                        """
        );
        assertSchemaRejected(
                cases.draftReview().requestFor(
                        fixture("draft-review", "unsupported-claim-boundary.json"))
                        .outputDefinition(),
                """
                        {"findings":[{"type":"UNKNOWN","targetExcerpt":"x",
                        "comment":"x","suggestion":"x"}],
                        "rewrite":{"content":" ","changedCount":-1}}
                        """
        );
    }

    @Test
    void actualValidatorsRejectUnknownEvidenceMissingCoverageAndInvalidEmptyCombinations() {
        ProductLiveEvaluationCases cases = cases();
        StructuredAiProviderRequest<IssueDetectionOutput> issue = cases.issueDetection()
                .requestFor(fixture("issue-detection", "direct-conflict-proposal.json"));
        assertThatThrownBy(() -> issue.outputDefinition().validator().validate(
                new IssueDetectionOutput(List.of(new IssueDetectionIssueOutput(
                        IssueType.GAP, "근거 없음", List.of(999_999L), null, List.of()
                ))),
                issue.validationContext()
        )).isInstanceOf(StructuredOutputSemanticException.class);

        StructuredAiProviderRequest<SynthesisAiOutput> synthesis = cases.opinionSynthesis()
                .requestFor(fixture("opinion-synthesis", "consensus-proposal.json"));
        assertThatThrownBy(() -> synthesis.outputDefinition().validator().validate(
                new SynthesisAiOutput("합의", List.of(10_001L), List.of(), List.of()),
                synthesis.validationContext()
        )).isInstanceOf(StructuredOutputSemanticException.class);

        StructuredAiProviderRequest<DraftGenerationOutput> draft = cases.draftGeneration()
                .requestFor(fixture("draft-generation", "unanswered-gap-proposal.json"));
        assertThatThrownBy(() -> draft.outputDefinition().validator().validate(
                new DraftGenerationOutput(
                        "미확인 marker가 없는 본문",
                        List.copyOf(draft.validationContext().allowedResourceIds()),
                        List.copyOf(draft.validationContext().allowedIssueIds()),
                        List.copyOf(draft.validationContext().allowedAnswerIds()),
                        List.copyOf(draft.validationContext().requiredUnresolvedIssueIds())
                ),
                draft.validationContext()
        )).isInstanceOf(StructuredOutputSemanticException.class);

        StructuredAiProviderRequest<DraftReviewOutput> review = cases.draftReview()
                .requestFor(fixture("draft-review", "no-findings-proposal.json"));
        assertThatThrownBy(() -> review.outputDefinition().validator().validate(
                new DraftReviewOutput(
                        List.of(),
                        new DraftReviewRewriteOutput("원문과 다른 rewrite", 1)
                ),
                review.validationContext()
        )).isInstanceOf(StructuredOutputSemanticException.class);
    }

    @Test
    void draftWithoutEvidenceLowersEvidenceCoverage() {
        ProductLiveEvaluationCases cases = cases();
        AiEvaluationFixture fixture = fixture(
                "draft-generation", "stale-input-boundary.json"
        );
        AiEvaluationCandidate candidate = cases.draftGeneration().normalize(
                fixture,
                new DraftGenerationOutput(
                        "근거가 연결되지 않은 초안",
                        List.of(), List.of(), List.of(), List.of()
                )
        );
        AiEvaluationSample sample = new AiEvaluationSample(
                fixture.metadata().id(),
                AiEvaluationOutcome.SUCCESS,
                candidate,
                new AiUsageMetadata("canned", "request", "model", 1L, 1L, 0L, 0L),
                AiCostSnapshot.unpriced("none"),
                1,
                1,
                List.of()
        );

        AiEvaluationMetrics metrics = new AiEvaluationMetricsCalculator().calculate(
                List.of(fixture), List.of(sample)
        );

        assertThat(candidate.claims()).singleElement()
                .satisfies(claim -> assertThat(claim.evidenceRequired()).isTrue());
        assertThat(metrics.evidenceCoverage().value()).isZero();
    }

    private ProductLiveEvaluationCases cases() {
        return new ProductLiveEvaluationCases(
                issueDetectionPromptFactory,
                synthesisPromptFactory,
                draftGenerationPromptFactory,
                draftReviewPromptFactory
        );
    }

    private AiEvaluationFixture fixture(String feature, String file) {
        return loader.load("ai/evaluation/" + feature + "/" + file);
    }

    private void assertSchemaRejected(StructuredOutputDefinition<?> definition, String json) {
        var mapper = JacksonUtils.getDefaultJsonMapper();
        var schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(mapper.readTree(definition.jsonSchema()));
        assertThat(schema.validate(mapper.readTree(json))).isNotEmpty();
    }
}
