package com.wevo.backend.review.dto.request;

import com.wevo.backend.review.domain.UnderstandingSignal;
import io.swagger.v3.oas.annotations.media.Schema;
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
 * (§6.2.3 "추가 코멘트는 선택 입력한다").
 *
 * <p>선택 입력 세 개(reviewerName·summary·reviewerComment) 모두 미입력과 공백만 입력한 경우를
 * {@code null} 하나로 정규화한다 — 저장 값과 응답이 "미입력"을 한 가지 형태로만 표현해야
 * 클라이언트가 빈 문자열과 null 을 따로 다루지 않는다. 정규화된 {@code null} 필드는 응답에서
 * 아예 생략된다. (CLAUDE.md §5.4)
 *
 * <p>이 이해도는 섹션 확정 조건에 포함되지 않는다.
 *
 * @param understandingSignal 이해도 (CLEAR/PARTIAL/UNCLEAR — 이해됨/애매함/이해 어려움)
 * @param reviewerName        선택 표시 이름
 * @param summary             이해한 핵심 한 문장 — CLEAR/PARTIAL 필수, UNCLEAR 선택
 * @param reviewerComment     추가 코멘트 — 항상 선택. 없으면 {@code null}
 */
@Schema(example = """
        {
          "understandingSignal": "PARTIAL",
          "reviewerName": "김검토",
          "summary": "협업 중 결정이 사라지는 문제를 다루는 것으로 이해했습니다.",
          "reviewerComment": "해결 방향이 조금 더 구체적이면 좋겠습니다."
        }""")
public record ExternalReviewSubmitRequest(
        @NotNull UnderstandingSignal understandingSignal,
        @Size(max = 100) String reviewerName,
        @Size(max = 1000) String summary,
        @Size(max = 1000) String reviewerComment
) {

    public ExternalReviewSubmitRequest {
        // 공백만 담긴 입력은 입력하지 않은 것과 같다. 선택 입력 세 개를 모두 여기서 null 로 모아,
        // 저장·응답·아래 필수 검증이 "미입력"을 한 가지 형태로만 보게 한다.
        reviewerName = blankToNull(reviewerName);
        summary = blankToNull(summary);
        reviewerComment = blankToNull(reviewerComment);
    }

    /**
     * 이해됨(CLEAR)·애매함(PARTIAL)으로 제출하면 이해한 핵심 문장이 있어야 한다.
     *
     * <p>공백 입력은 생성자에서 이미 {@code null} 로 정규화되므로 여기서는 null 여부만 본다.
     */
    @AssertTrue(message = "이해한 내용을 한 문장으로 적어주세요.")
    public boolean isSummaryPresentForUnderstoodSignal() {
        if (understandingSignal != UnderstandingSignal.CLEAR
                && understandingSignal != UnderstandingSignal.PARTIAL) {
            return true;
        }
        return summary != null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
