package com.wevo.backend.ai.context;

/** 초안 생성 근거에 포함되는 직접·승계 GAP 답변 스냅샷. */
public record AiDraftGapAnswerContext(
        Long sourceIssueId,
        Long answerId,
        String content,
        String answeredAt,
        String authorName,
        boolean inherited
) {
}
