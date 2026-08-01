package com.wevo.backend.review.dto.request;

import com.wevo.backend.review.domain.UnderstandingSignal;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 외부 검토자의 이해도 제출 요청.
 *
 * <p>이해도 신호(understandingSignal)는 필수, 표시 이름은 선택이다.
 * summary(이해한 핵심 한 문장)는 <b>이해됨(CLEAR)·애매함(PARTIAL)이면 필수</b>이고,
 * 이해 어려움(UNCLEAR)일 때만 선택이다 — 정책서 §6.2.3 "이해한 핵심 한 문장을 함께 남긴다
 * (이해됨·애매함은 필수, 이해 어려움은 선택)". 같은 절의 "추가 코멘트는 선택"은 이 문장이 아니라
 * <b>별도의 자유 코멘트</b>를 가리키므로 여기에 적용하지 않는다.
 *
 * <p>이해했다고 답한 검토자에게 무엇을 이해했는지 받지 못하면 작성자 의도와 대조할 대상이 없어
 * 의도 vs 이해 비교가 성립하지 않는다 — summary 가 비면 비교는 {@code NOT_AVAILABLE} 로 끝난다.
 * 반대로 UNCLEAR 는 "이해하지 못했다"는 답 자체가 신호라 문장을 강제하지 않는다.
 *
 * <p>추가 코멘트(reviewerComment)는 summary 와 별개 입력이며 <b>이해도와 무관하게 항상 선택</b>이다
 * (§6.2.3 "추가 코멘트는 선택 입력한다"). 미입력·공백만 입력한 경우 모두 {@code null} 로 저장해
 * "코멘트 없음"을 한 가지 형태로만 표현한다.
 *
 * <p>이 이해도는 섹션 확정 조건에 포함되지 않는다.
 *
 * @param understandingSignal 이해도 (CLEAR/PARTIAL/UNCLEAR — 이해됨/애매함/이해 어려움)
 * @param reviewerName        선택 표시 이름
 * @param summary             이해한 핵심 한 문장 — CLEAR/PARTIAL 필수, UNCLEAR 선택
 * @param reviewerComment     추가 코멘트 — 항상 선택. 없으면 {@code null}
 */
public record ExternalReviewSubmitRequest(
        @NotNull UnderstandingSignal understandingSignal,
        @Size(max = 100) String reviewerName,
        @Size(max = 1000) String summary,
        @Size(max = 1000) String reviewerComment
) {

    public ExternalReviewSubmitRequest {
        // 공백만 담긴 코멘트는 입력하지 않은 것과 같다. 저장·응답 모두에서 null 한 가지로 다룬다.
        if (reviewerComment != null && reviewerComment.isBlank()) {
            reviewerComment = null;
        }
    }

    /** 이해됨(CLEAR)·애매함(PARTIAL)으로 제출하면 이해한 핵심 문장이 있어야 한다. */
    @AssertTrue(message = "이해한 내용을 한 문장으로 적어주세요.")
    public boolean isSummaryPresentForUnderstoodSignal() {
        if (understandingSignal != UnderstandingSignal.CLEAR
                && understandingSignal != UnderstandingSignal.PARTIAL) {
            return true;
        }
        return summary != null && !summary.isBlank();
    }
}
