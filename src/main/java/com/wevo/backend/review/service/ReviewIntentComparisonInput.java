package com.wevo.backend.review.service;

/** AI 도메인에 공개하는 최소 비교 입력. 개인정보와 다른 제출은 포함하지 않는다. */
public record ReviewIntentComparisonInput(
        Long submissionId,
        Long projectId,
        Long sectionId,
        Long requestedByUserId,
        int contentVersion,
        String authorIntent,
        String reviewerSummary
) {
}
