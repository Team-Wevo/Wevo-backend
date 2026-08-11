package com.wevo.backend.review.service;

/** 오래된 PENDING 비교를 회수하기 위한 최소 식별자. */
public record ReviewIntentComparisonRecoveryCandidate(
        Long submissionId,
        Long sourceAiJobId
) {

    public ReviewIntentComparisonRecoveryCandidate {
        if (submissionId == null) {
            throw new IllegalArgumentException("submissionId는 필수입니다.");
        }
    }
}
