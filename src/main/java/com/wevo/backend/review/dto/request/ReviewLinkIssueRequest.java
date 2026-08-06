package com.wevo.backend.review.dto.request;

import java.time.LocalDate;

/**
 * 외부 검토 링크 발급 요청. (팀장 전용)
 *
 * <p>본문 자체가 선택이다 — 본문 없이 호출하면 유효 기간 없는 링크를 발급한다(기존 동작).
 *
 * @param expiresOn 링크가 살아 있는 <b>마지막 날</b> (KST, {@code yyyy-MM-dd}). 선택 —
 *                  생략하면 기간 제한이 없다. 지정하면 <b>발급일보다 최소 1일 뒤</b>여야 하며,
 *                  당일·과거 날짜는 422({@code C002})로 거부한다.
 *                  유효 기간은 날짜 단위로만 지정하므로 시:분은 받지 않는다.
 *                  <b>빈 문자열({@code ""})과 {@code null} 은 생략과 같게 "기간 제한 없음"으로
 *                  읽는다</b> — 기간 입력칸을 비운 채 보내는 폼 제출을 400 으로 튕기지 않기 위함이다.
 *                  (빈 문자열 → {@code null} 변환은 Jackson 의 {@code LocalDate} 기본 동작)
 */
public record ReviewLinkIssueRequest(
        LocalDate expiresOn
) {
}
