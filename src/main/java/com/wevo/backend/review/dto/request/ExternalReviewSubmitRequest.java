package com.wevo.backend.review.dto.request;

import com.wevo.backend.review.domain.UnderstandingSignal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 외부 검토자의 이해도 제출 요청.
 *
 * <p>이해도 신호(understandingSignal)는 필수, 표시 이름과 summary(이해한 핵심 한 문장)는
 * <b>항상 선택</b>이다 — 정책서 §6.2 "코멘트는 항상 선택". 이해도별 필수 강제는 두지 않는다.
 * 이 이해도는 섹션 확정 조건에 포함되지 않는다.
 *
 * @param understandingSignal 이해도 (CLEAR/PARTIAL/UNCLEAR — 이해됨/애매함/이해 어려움)
 * @param reviewerName        선택 표시 이름
 * @param summary             이해한 핵심 한 문장 — 선택 (§6.2)
 */
public record ExternalReviewSubmitRequest(
        @NotNull UnderstandingSignal understandingSignal,
        @Size(max = 100) String reviewerName,
        @Size(max = 1000) String summary
) {
}
