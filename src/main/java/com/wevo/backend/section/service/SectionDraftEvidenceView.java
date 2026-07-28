package com.wevo.backend.section.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 생성 시점에 고정된 aggregate 초안 근거 조회 모델. */
public record SectionDraftEvidenceView(
        Long draftId,
        int contentVersion,
        Long synthesisSetId,
        long opinionGateGeneration,
        UUID generationRequestId,
        String inputSnapshotHash,
        String consensusSummary,
        List<OpinionEvidence> opinions,
        List<DecisionEvidence> decisions,
        List<GapAnswerEvidence> gapAnswers
) {

    public SectionDraftEvidenceView {
        opinions = List.copyOf(opinions);
        decisions = List.copyOf(decisions);
        gapAnswers = List.copyOf(gapAnswers);
    }

    public record OpinionEvidence(Long opinionId, String authorName, String content) {
    }

    public record DecisionEvidence(Long issueId, String question, String decision) {
    }

    public record GapAnswerEvidence(
            Long sourceIssueId,
            Long answerId,
            String authorName,
            String content,
            LocalDateTime answeredAt,
            boolean inherited
    ) {
    }
}
