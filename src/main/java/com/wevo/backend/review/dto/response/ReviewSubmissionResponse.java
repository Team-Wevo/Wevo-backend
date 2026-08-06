package com.wevo.backend.review.dto.response;

import com.wevo.backend.review.domain.ReviewSubmission;
import com.wevo.backend.review.domain.UnderstandingSignal;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 외부 검토 제출 결과.
 *
 * @param submissionId        제출 식별자
 * @param understandingSignal 제출된 이해도
 */
@Schema(requiredProperties = {"submissionId", "understandingSignal"})
public record ReviewSubmissionResponse(Long submissionId, UnderstandingSignal understandingSignal) {

    public static ReviewSubmissionResponse from(ReviewSubmission submission) {
        return new ReviewSubmissionResponse(submission.getId(), submission.getUnderstandingSignal());
    }
}
