package com.wevo.backend.issue.service;

import java.util.List;

/** section의 가장 최근 성공 synthesis set과 해당 set이 사용하는 GAP 답변 합집합. */
public record CurrentSynthesisContext(
        Long synthesisSetId,
        long opinionGateGeneration,
        String consensusSummary,
        List<GapAnswerContext> gapAnswers,
        List<SynthesisOpinionEvidenceContext> opinionEvidence,
        List<ConflictDecisionContext> conflictDecisions,
        List<GapIssueContext> gapIssues,
        boolean hasUnresolvedConflict
) {

    public CurrentSynthesisContext {
        gapAnswers = List.copyOf(gapAnswers);
        opinionEvidence = List.copyOf(opinionEvidence);
        conflictDecisions = List.copyOf(conflictDecisions);
        gapIssues = List.copyOf(gapIssues);
    }

    public CurrentSynthesisContext(
            Long synthesisSetId,
            long opinionGateGeneration,
            String consensusSummary,
            List<GapAnswerContext> gapAnswers
    ) {
        this(
                synthesisSetId,
                opinionGateGeneration,
                consensusSummary,
                gapAnswers,
                List.of(),
                List.of(),
                List.of(),
                false
        );
    }
}
