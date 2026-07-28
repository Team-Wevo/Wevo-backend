package com.wevo.backend.issue.service;

import java.time.LocalDateTime;

/** AI 입력에 허용된 직접·승계 GAP 답변 최소 정보. */
public record GapAnswerContext(
        Long sourceIssueId,
        Long answerId,
        String content,
        LocalDateTime answeredAt,
        String authorNameSnapshot,
        boolean inherited
) {

    public GapAnswerContext(
            Long sourceIssueId,
            Long answerId,
            String content,
            LocalDateTime answeredAt
    ) {
        this(sourceIssueId, answerId, content, answeredAt, "알 수 없음", false);
    }
}
