package com.wevo.backend.ai.evaluation;

import java.util.List;
import java.util.Set;

public record AiEvaluationCandidate(
        List<DetectedIssue> issues,
        List<Claim> claims,
        Set<String> reviewIssueIds,
        int mergedConflictCount,
        int unverifiedFactCount
) {

    public AiEvaluationCandidate {
        issues = issues == null ? List.of() : List.copyOf(issues);
        claims = claims == null ? List.of() : List.copyOf(claims);
        reviewIssueIds = reviewIssueIds == null ? Set.of() : Set.copyOf(reviewIssueIds);
        if (mergedConflictCount < 0 || unverifiedFactCount < 0) {
            throw new IllegalArgumentException("품질 오류 건수는 0 이상이어야 합니다.");
        }
    }

    public static AiEvaluationCandidate empty() {
        return new AiEvaluationCandidate(List.of(), List.of(), Set.of(), 0, 0);
    }

    public record DetectedIssue(
            AiEvaluationFixture.IssueType type,
            Set<String> evidenceIds
    ) {
        public DetectedIssue {
            if (type == null) {
                throw new IllegalArgumentException("쟁점 유형은 필수입니다.");
            }
            evidenceIds = evidenceIds == null ? Set.of() : Set.copyOf(evidenceIds);
        }
    }

    public record Claim(
            String text,
            boolean evidenceRequired,
            Set<String> evidenceIds
    ) {
        public Claim {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("평가용 claim text는 필수입니다.");
            }
            evidenceIds = evidenceIds == null ? Set.of() : Set.copyOf(evidenceIds);
        }
    }
}
