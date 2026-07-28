package com.wevo.backend.ai.context;

import java.util.List;

/** 해소된 CONFLICT와 OWNER 결정. */
public record AiConflictDecisionContext(
        Long issueId,
        Long decisionId,
        String description,
        String question,
        String decision,
        List<Long> evidenceOpinionIds
) {

    public AiConflictDecisionContext {
        evidenceOpinionIds = List.copyOf(evidenceOpinionIds);
    }
}
