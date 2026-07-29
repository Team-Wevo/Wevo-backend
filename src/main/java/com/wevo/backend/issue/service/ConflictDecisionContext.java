package com.wevo.backend.issue.service;

import java.util.List;

/** current synthesis의 해소된 CONFLICT와 OWNER 결정. */
public record ConflictDecisionContext(
        Long issueId,
        Long decisionId,
        String description,
        String question,
        String decision,
        List<Long> evidenceOpinionIds
) {

    public ConflictDecisionContext {
        evidenceOpinionIds = List.copyOf(evidenceOpinionIds);
    }
}
