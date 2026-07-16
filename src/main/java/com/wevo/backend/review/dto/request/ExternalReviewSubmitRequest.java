package com.wevo.backend.review.dto.request;

import com.wevo.backend.review.domain.UnderstandingSignal;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 외부 검토자의 이해도 제출 요청.
 *
 * <p>이해도 신호(understandingSignal)는 필수, 표시 이름은 선택이다.
 * summary(이해한 핵심 한 문장)는 <b>이해도에 따라 필수/선택이 갈린다.</b>
 * (이해됨·애매함이면 필수, 이해 어려움이면 선택)
 * 이 이해도는 섹션 확정 조건에 포함되지 않는다.
 *
 * @param understandingSignal 이해도 (CLEAR/PARTIAL/UNCLEAR — 이해됨/애매함/이해 어려움)
 * @param reviewerName        선택 표시 이름
 * @param summary             이해한 핵심 한 문장 — CLEAR·PARTIAL 필수, UNCLEAR 선택
 */
public record ExternalReviewSubmitRequest(
        @NotNull UnderstandingSignal understandingSignal,
        @Size(max = 100) String reviewerName,
        @Size(max = 1000) String summary
) {

    /**
     * summary 의 이해도별 필수 여부를 검증한다. (길이 제약으로는 표현할 수 없는 교차 검증)
     *
     * <ul>
     *   <li>{@code CLEAR}(이해됨)·{@code PARTIAL}(애매함) — 무엇을 이해했는지 한 문장이 <b>있어야 한다.</b></li>
     *   <li>{@code UNCLEAR}(이해 어려움) — 선택. 이해가 어려워 요약이 없을 수 있다.</li>
     * </ul>
     *
     * <p>{@code understandingSignal} 이 null 인 경우는 {@link NotNull} 이 따로 처리하므로 통과시킨다.
     */
    @AssertTrue(message = "이해한 핵심 한 문장을 입력해주세요.")
    public boolean isSummaryPresentForSignal() {
        if (understandingSignal == null) {
            return true;
        }
        boolean summaryRequired = understandingSignal == UnderstandingSignal.CLEAR
                || understandingSignal == UnderstandingSignal.PARTIAL;
        return !summaryRequired || (summary != null && !summary.isBlank());
    }
}
