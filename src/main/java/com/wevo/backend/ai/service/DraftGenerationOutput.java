package com.wevo.backend.ai.service;

import java.util.List;

/** AI 초안 생성의 Provider 구조화 출력. API DTO와 영속 엔티티로 직접 사용하지 않는다. */
public record DraftGenerationOutput(
        String content,
        List<Long> evidenceOpinionIds,
        List<Long> evidenceIssueIds,
        List<Long> evidenceAnswerIds,
        List<Long> unresolvedGapIssueIds
) {

    public DraftGenerationOutput {
        evidenceOpinionIds = copy(evidenceOpinionIds);
        evidenceIssueIds = copy(evidenceIssueIds);
        evidenceAnswerIds = copy(evidenceAnswerIds);
        unresolvedGapIssueIds = copy(unresolvedGapIssueIds);
    }

    private static List<Long> copy(List<Long> values) {
        return values == null ? null : List.copyOf(values);
    }
}
