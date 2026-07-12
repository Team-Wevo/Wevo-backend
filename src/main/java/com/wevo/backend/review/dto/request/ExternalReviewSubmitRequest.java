package com.wevo.backend.review.dto.request;

import com.wevo.backend.review.domain.UnderstandingSignal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 외부 검토자의 이해도 제출 요청.
 *
 * <p>이해도 신호(understandingSignal)만 필수이고, 코멘트와 표시 이름은 선택이다.
 * 이 이해도는 섹션 확정 조건에 포함되지 않는다.
 *
 * @param understandingSignal 이해도 (CLEAR/PARTIAL/UNCLEAR — UI: 이해됨/애매함/이해 어려움)
 * @param reviewerName           선택 표시 이름
 * @param summary       선택 코멘트
 */
public record ExternalReviewSubmitRequest(
        @NotNull UnderstandingSignal understandingSignal,
        @Size(max = 100) String reviewerName,
        @Size(max = 1000) String summary
) {
}
