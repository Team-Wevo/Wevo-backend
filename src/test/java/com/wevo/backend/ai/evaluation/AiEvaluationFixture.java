package com.wevo.backend.ai.evaluation;

import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.project.domain.OutputType;

import java.util.List;
import java.util.Set;

public record AiEvaluationFixture(
        int schemaVersion,
        Metadata metadata,
        Input input,
        Expected expected
) {

    public record Metadata(
            String id,
            AiFeature feature,
            String description,
            String language,
            Set<String> tags,
            String datasetVersion,
            Kind kind,
            boolean synthetic
    ) {
        public Metadata {
            tags = tags == null ? Set.of() : Set.copyOf(tags);
        }
    }

    public record Input(
            OutputType outputType,
            String projectContext,
            String sectionContext,
            List<String> labels,
            List<Opinion> opinions,
            String parentConfirmedContent
    ) {
        public Input {
            labels = labels == null ? List.of() : List.copyOf(labels);
            opinions = opinions == null ? List.of() : List.copyOf(opinions);
        }

        public Set<String> allowedEvidenceIds() {
            return opinions.stream()
                    .filter(Opinion::submitted)
                    .filter(opinion -> !opinion.deleted())
                    .map(Opinion::id)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }

    public record Opinion(
            String id,
            String content,
            boolean submitted,
            boolean deleted
    ) {
    }

    public record Expected(
            List<String> requiredFacts,
            List<ExpectedIssue> expectedIssues,
            Set<String> expectedEvidenceIds,
            List<String> forbiddenClaims,
            Set<String> goldReviewIssueIds
    ) {
        public Expected {
            requiredFacts = requiredFacts == null ? List.of() : List.copyOf(requiredFacts);
            expectedIssues = expectedIssues == null ? List.of() : List.copyOf(expectedIssues);
            expectedEvidenceIds = expectedEvidenceIds == null ? Set.of() : Set.copyOf(expectedEvidenceIds);
            forbiddenClaims = forbiddenClaims == null ? List.of() : List.copyOf(forbiddenClaims);
            goldReviewIssueIds = goldReviewIssueIds == null ? Set.of() : Set.copyOf(goldReviewIssueIds);
        }
    }

    public record ExpectedIssue(
            IssueType type,
            Set<String> evidenceIds
    ) {
        public ExpectedIssue {
            evidenceIds = evidenceIds == null ? Set.of() : Set.copyOf(evidenceIds);
        }
    }

    public enum Kind {
        CONTRACT,
        QUALITY
    }

    public enum IssueType {
        CONFLICT,
        GAP
    }
}
