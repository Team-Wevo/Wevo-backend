package com.wevo.backend.ai.context;

import java.util.List;

/** current synthesis의 GAP. {@code answered=false}이면 본문에 미확인으로 남긴다. */
public record AiGapIssueContext(
        Long issueId,
        String description,
        boolean answered,
        List<Long> evidenceOpinionIds
) {

    public AiGapIssueContext {
        evidenceOpinionIds = List.copyOf(evidenceOpinionIds);
    }
}
