package com.wevo.backend.review.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wevo.backend.review.domain.ReviewIntentAlignment;
import com.wevo.backend.review.domain.ReviewIntentComparison;
import com.wevo.backend.review.domain.ReviewIntentComparisonStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 작성자 의도와 외부 검토자 이해의 비교 결과.
 *
 * <p>필수는 {@code status} 하나뿐이다 — 비교가 아직 없거나({@code NOT_AVAILABLE}) 실패하면
 * 나머지가 전부 비므로, FE 는 {@code status} 로 먼저 분기해야 한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(requiredProperties = {"status"})
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
