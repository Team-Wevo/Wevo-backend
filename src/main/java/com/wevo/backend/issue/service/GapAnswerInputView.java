package com.wevo.backend.issue.service;

/**
 * AI 재정리 입력에 포함되는 GAP 보충 근거 답변의 읽기 전용 투영. (§3.8.1 GAP 답변 승계)
 *
 * <p>{@code answerId}는 중복 제거·승계 원본 참조 저장에 쓰고, {@code sourceIssueId}는 새 세트의
 * {@code inheritedGapAnswers} 원본 참조에 기록한다(근거 추적). {@code authorName}·{@code content}는
 * AI 입력 본문이다.
 *
 * @param answerId      원본 답변 ID (승계·중복 제거 기준)
 * @param sourceIssueId 답변이 속한 원본 쟁점 ID
 * @param authorName    답변자 이름 스냅샷
 * @param content       보충 근거 본문
 */
public record GapAnswerInputView(
        Long answerId,
        Long sourceIssueId,
        String authorName,
        String content
) {
}
