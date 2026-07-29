package com.wevo.backend.ai.context;

import java.util.List;

/** 초안 생성에 필요한 current synthesis의 합의·결정·GAP·의견 근거 전체. */
public record AiDraftSynthesisContext(
        Long synthesisSetId,
        long opinionGateGeneration,
        String consensusSummary,
        List<AiDraftGapAnswerContext> gapAnswers,
        List<AiOpinionEvidenceContext> opinionEvidence,
        List<AiConflictDecisionContext> conflictDecisions,
        List<AiGapIssueContext> gapIssues,
        boolean hasUnresolvedConflict
) {

    public AiDraftSynthesisContext {
        gapAnswers = List.copyOf(gapAnswers);
        opinionEvidence = List.copyOf(opinionEvidence);
        conflictDecisions = List.copyOf(conflictDecisions);
        gapIssues = List.copyOf(gapIssues);
    }
}
