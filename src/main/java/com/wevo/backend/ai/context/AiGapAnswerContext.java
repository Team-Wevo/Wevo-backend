package com.wevo.backend.ai.context;

/** 현재 synthesis에서 유효한 직접·승계 GAP 답변. */
public record AiGapAnswerContext(
        Long sourceIssueId,
        Long answerId,
        String content,
        String answeredAt
) {
}
