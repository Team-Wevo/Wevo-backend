package com.wevo.backend.issue.service;

import java.util.List;

/** current synthesis의 GAP 쟁점. 답변이 없으면 초안에서 미확인 항목으로 남겨야 한다. */
public record GapIssueContext(
        Long issueId,
        String description,
        boolean answered,
        List<Long> evidenceOpinionIds
) {

    public GapIssueContext {
        evidenceOpinionIds = List.copyOf(evidenceOpinionIds);
    }
}
