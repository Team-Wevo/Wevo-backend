package com.wevo.backend.review.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wevo.backend.review.domain.ReviewSubmission;
import com.wevo.backend.review.domain.ReviewIntentComparison;
import com.wevo.backend.review.domain.UnderstandingSignal;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 외부 검토 제출 1건. (팀장이 결과를 조회할 때 노출)
 *
 * <p>미입력 선택 항목은 {@code null} 로 싣지 않고 <b>키 자체를 생략</b>한다 (CLAUDE.md §5.4).
 * 요청 DTO 가 공백 입력을 {@code null} 로 정규화하므로, 클라이언트는 "값 없음"을
 * 키 부재 한 가지로만 판단하면 된다.
 *
 * @param submissionId        제출 식별자
 * @param understandingSignal 이해도 (CLEAR/PARTIAL/UNCLEAR)
 * @param reviewerName        외부 검토자 표시 이름 (없을 수 있음)
 * @param summary             외부 검토자가 이해한 핵심 한 문장
 *                            (CLEAR/PARTIAL 은 제출 시 필수라 항상 있고, UNCLEAR 는 없을 수 있음)
 * @param reviewerComment     외부 검토자가 덧붙인 추가 코멘트 — 항상 선택이라 없을 수 있음
 * @param contentVersion      검토한 본문 버전 (제출이 달린 링크에 고정된 발급 시점 버전)
 * @param submittedAt         제출 시각
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(requiredProperties = {
        "submissionId", "understandingSignal", "contentVersion", "submittedAt", "comparison"
})
public record ExternalReviewItemResponse(
        Long submissionId,
        UnderstandingSignal understandingSignal,
        String reviewerName,
        String summary,
        String reviewerComment,
        Integer contentVersion,
        LocalDateTime submittedAt,
        String authorIntent,
        ReviewIntentComparisonResponse comparison
) {

    public static ExternalReviewItemResponse from(
            ReviewSubmission submission,
            ReviewIntentComparison comparison
    ) {
        return new ExternalReviewItemResponse(
                submission.getId(),
                submission.getUnderstandingSignal(),
                submission.getReviewerName(),
                submission.getSummary(),
                submission.getReviewerComment(),
                submission.getReviewLink().getContentVersion(),
                submission.getCreatedAt(),
                submission.getReviewLink().getAuthorIntentSnapshot(),
                ReviewIntentComparisonResponse.from(comparison));
    }
}
