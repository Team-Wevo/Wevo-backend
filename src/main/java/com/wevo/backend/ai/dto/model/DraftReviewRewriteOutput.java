package com.wevo.backend.ai.dto.model;

/** AI 사전 검토가 제안하는 전체 본문 rewrite. */
public record DraftReviewRewriteOutput(
        String content,
        int changedCount
) {
}
