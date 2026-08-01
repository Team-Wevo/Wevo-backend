package com.wevo.backend.review.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wevo.backend.review.domain.ReviewIntentAlignment;
import com.wevo.backend.review.domain.ReviewIntentComparison;
import com.wevo.backend.review.domain.ReviewIntentComparisonStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReviewIntentComparisonResponse(
        ReviewIntentComparisonStatus status,
        ReviewIntentAlignment alignment,
        String differenceSummary,
        String evidenceExcerpt,
        String failureCode
) {

    public static ReviewIntentComparisonResponse from(ReviewIntentComparison comparison) {
        if (comparison == null) {
            return new ReviewIntentComparisonResponse(
                    ReviewIntentComparisonStatus.NOT_AVAILABLE, null, null, null, null);
        }
        return new ReviewIntentComparisonResponse(
                comparison.getStatus(),
                comparison.getAlignment(),
                comparison.getDifferenceSummary(),
                comparison.getEvidenceExcerpt(),
                comparison.getFailureCode());
    }
}
