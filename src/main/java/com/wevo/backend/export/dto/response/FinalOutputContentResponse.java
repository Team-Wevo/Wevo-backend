package com.wevo.backend.export.dto.response;

/**
 * 완성본을 클립보드에 그대로 넣을 수 있게 조립한 문자열 응답. (§3.6.2 텍스트 · §3.6.3 마크다운)
 *
 * <p>파일 다운로드가 아니라 <b>복사용 조회</b>이므로 {@code ApiResponse} 래퍼를 적용한다.
 * (CLAUDE.md §5.3 — 래퍼 적용 여부는 응답 형식 기준) FE 는 이 값을 가공하지 않고 그대로
 * 클립보드에 넣는다.
 *
 * @param content 조립된 완성본 전체 문자열
 */
public record FinalOutputContentResponse(String content) {
}
