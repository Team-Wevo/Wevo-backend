package com.wevo.backend.section.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** AI 계층이 section 도메인에 전달하는 Provider 중립 초안 생성 명령. */
public record AiSectionDraftCreateCommand(
        Long sectionId,
        Long actorUserId,
        String content,
        int baseVersion,
        Long synthesisSetId,
        long opinionGateGeneration,
        UUID generationRequestId,
        String inputSnapshotHash,
        String sourceVersion,
        String consensusSummary,
        List<OpinionEvidence> opinions,
        List<DecisionEvidence> decisions,
        List<GapAnswerEvidence> gapAnswers,
        List<PrerequisiteEvidence> prerequisites
) {

    public AiSectionDraftCreateCommand {
        opinions = List.copyOf(opinions);
        decisions = List.copyOf(decisions);
        gapAnswers = List.copyOf(gapAnswers);
        prerequisites = List.copyOf(prerequisites);
    }

    public record OpinionEvidence(Long opinionId, String authorName, String content) {
    }

    public record DecisionEvidence(
            Long issueId,
            Long decisionId,
            String question,
            String decision
    ) {
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

    public record PrerequisiteEvidence(Long sectionId, int contentVersion) {
    }
}
