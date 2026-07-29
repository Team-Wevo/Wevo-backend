package com.wevo.backend.ai.dto.model;

import java.util.List;

/** AI 사전 검토 Provider 구조화 출력. API DTO와 영속 엔티티로 직접 사용하지 않는다. */
public record DraftReviewOutput(
        List<DraftReviewFindingOutput> findings,
        DraftReviewRewriteOutput rewrite
) {

    public DraftReviewOutput {
        findings = findings == null ? null : List.copyOf(findings);
    }
}
