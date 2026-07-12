package com.wevo.backend.review.dto.response;

import com.wevo.backend.review.domain.ReviewSubmission;
import com.wevo.backend.review.domain.UnderstandingSignal;
import java.time.LocalDateTime;

/**
 * 외부 검토 제출 1건. (팀장이 결과를 조회할 때 노출)
 *
 * @param submissionId        제출 식별자
 * @param understandingSignal 이해도 (CLEAR/PARTIAL/UNCLEAR)
 * @param reviewerName        외부 검토자 표시 이름 (없을 수 있음)
 * @param summary             외부 검토자 코멘트 (없을 수 있음)
 * @param submittedAt         제출 시각
 */
public record ExternalReviewItemResponse(
        Long submissionId,
        UnderstandingSignal understandingSignal,
        String reviewerName,
        String summary,
        LocalDateTime submittedAt
) {

    public static ExternalReviewItemResponse from(ReviewSubmission submission) {
        return new ExternalReviewItemResponse(
                submission.getId(),
                submission.getUnderstandingSignal(),
                submission.getReviewerName(),
                submission.getSummary(),
                submission.getCreatedAt());
    }
}
