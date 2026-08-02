package com.wevo.backend.ai.evaluation;

import com.wevo.backend.ai.dto.model.DraftReviewFindingOutput;
import com.wevo.backend.ai.dto.model.DraftReviewOutput;
import com.wevo.backend.ai.dto.model.IssueDetectionIssueOutput;
import com.wevo.backend.ai.dto.model.IssueDetectionOutput;
import com.wevo.backend.ai.prompt.DraftGenerationPromptFactory;
import com.wevo.backend.ai.prompt.DraftReviewPromptFactory;
import com.wevo.backend.ai.prompt.IssueDetectionPromptFactory;
import com.wevo.backend.ai.prompt.SynthesisPromptFactory;
import com.wevo.backend.ai.service.DraftGenerationOutput;
import com.wevo.backend.ai.service.SynthesisAiOutput;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** 네 제품 기능의 실제 prompt factory, output definition, validator를 live 평가에 연결한다. */
final class ProductLiveEvaluationCases {

    private final IssueDetectionPromptFactory issueDetectionPromptFactory;
    private final SynthesisPromptFactory synthesisPromptFactory;
    private final DraftGenerationPromptFactory draftGenerationPromptFactory;
    private final DraftReviewPromptFactory draftReviewPromptFactory;
    private final SyntheticEvaluationContextFactory contexts;

    ProductLiveEvaluationCases(
            IssueDetectionPromptFactory issueDetectionPromptFactory,
            SynthesisPromptFactory synthesisPromptFactory,
            DraftGenerationPromptFactory draftGenerationPromptFactory,
            DraftReviewPromptFactory draftReviewPromptFactory
    ) {
        this.issueDetectionPromptFactory = issueDetectionPromptFactory;
        this.synthesisPromptFactory = synthesisPromptFactory;
        this.draftGenerationPromptFactory = draftGenerationPromptFactory;
        this.draftReviewPromptFactory = draftReviewPromptFactory;
        this.contexts = new SyntheticEvaluationContextFactory();
    }

    LiveEvaluationCase<IssueDetectionOutput> issueDetection() {
        return new LiveEvaluationCase<>() {
            @Override
            public com.wevo.backend.ai.client.StructuredAiProviderRequest<IssueDetectionOutput> requestFor(
                    AiEvaluationFixture fixture
            ) {
                return issueDetectionPromptFactory.providerRequest(contexts.issueDetection(fixture));
            }

            @Override
            public AiEvaluationCandidate normalize(
                    AiEvaluationFixture fixture,
                    IssueDetectionOutput result
            ) {
                return candidateForIssues(fixture, result.issues(), List.of());
            }
        };
    }

    LiveEvaluationCase<SynthesisAiOutput> opinionSynthesis() {
        return new LiveEvaluationCase<>() {
            @Override
            public com.wevo.backend.ai.client.StructuredAiProviderRequest<SynthesisAiOutput> requestFor(
                    AiEvaluationFixture fixture
            ) {
                return synthesisPromptFactory.providerRequest(
                        contexts.synthesis(fixture), contexts.allowedOpinionIds(fixture)
                );
            }

            @Override
            public AiEvaluationCandidate normalize(
                    AiEvaluationFixture fixture,
                    SynthesisAiOutput result
            ) {
                AiEvaluationCandidate.Claim consensus = new AiEvaluationCandidate.Claim(
                        result.consensusSummary(),
                        true,
                        evidenceIds(fixture, result.consensusEvidenceOpinionIds())
                );
                return candidateForIssues(fixture, result.issues(), List.of(consensus));
            }
        };
    }

    LiveEvaluationCase<DraftGenerationOutput> draftGeneration() {
        return new LiveEvaluationCase<>() {
            @Override
            public com.wevo.backend.ai.client.StructuredAiProviderRequest<DraftGenerationOutput> requestFor(
                    AiEvaluationFixture fixture
            ) {
                return draftGenerationPromptFactory.providerRequest(contexts.draftGeneration(fixture));
            }

            @Override
            public AiEvaluationCandidate normalize(
                    AiEvaluationFixture fixture,
                    DraftGenerationOutput result
            ) {
                Set<String> evidence = evidenceIds(fixture, result.evidenceOpinionIds());
                return new AiEvaluationCandidate(
                        List.of(),
                        List.of(new AiEvaluationCandidate.Claim(
                                result.content(), true, evidence
                        )),
                        Set.of(),
                        0,
                        0
                );
            }
        };
    }

    LiveEvaluationCase<DraftReviewOutput> draftReview() {
        return new LiveEvaluationCase<>() {
            @Override
            public com.wevo.backend.ai.client.StructuredAiProviderRequest<DraftReviewOutput> requestFor(
                    AiEvaluationFixture fixture
            ) {
                return draftReviewPromptFactory.providerRequest(contexts.draftReview(fixture));
            }

            @Override
            public AiEvaluationCandidate normalize(
                    AiEvaluationFixture fixture,
                    DraftReviewOutput result
            ) {
                Set<String> reviewIds = result.findings().stream()
                        .map(DraftReviewFindingOutput::type)
                        .map(type -> "review-" + type.name().toLowerCase(Locale.ROOT)
                                .replace('_', '-'))
                        .collect(Collectors.toUnmodifiableSet());
                List<AiEvaluationCandidate.Claim> claims = result.findings().stream()
                        .map(finding -> new AiEvaluationCandidate.Claim(
                                finding.targetExcerpt() + " " + finding.comment(),
                                false,
                                Set.of()
                        ))
                        .toList();
                return new AiEvaluationCandidate(List.of(), claims, reviewIds, 0, 0);
            }
        };
    }

    private AiEvaluationCandidate candidateForIssues(
            AiEvaluationFixture fixture,
            List<IssueDetectionIssueOutput> issues,
            List<AiEvaluationCandidate.Claim> additionalClaims
    ) {
        List<AiEvaluationCandidate.DetectedIssue> detected = issues.stream()
                .map(issue -> new AiEvaluationCandidate.DetectedIssue(
                        AiEvaluationFixture.IssueType.valueOf(issue.type().name()),
                        evidenceIds(fixture, issue.evidenceOpinionIds())
                ))
                .toList();
        List<AiEvaluationCandidate.Claim> claims = new java.util.ArrayList<>(additionalClaims);
        issues.forEach(issue -> claims.add(new AiEvaluationCandidate.Claim(
                issue.description(),
                true,
                evidenceIds(fixture, issue.evidenceOpinionIds())
        )));
        return new AiEvaluationCandidate(detected, claims, Set.of(), 0, 0);
    }

    private Set<String> evidenceIds(AiEvaluationFixture fixture, List<Long> internalIds) {
        return internalIds.stream()
                .map(id -> contexts.externalOpinionId(fixture, id))
                .collect(Collectors.toUnmodifiableSet());
    }
}
