package com.wevo.backend.issue.service;

import java.util.List;

/** section의 가장 최근 성공 synthesis set과 해당 set이 사용하는 GAP 답변 합집합. */
public record CurrentSynthesisContext(
        Long synthesisSetId,
        long opinionGateGeneration,
        String consensusSummary,
        List<GapAnswerContext> gapAnswers
) {

    public CurrentSynthesisContext {
        gapAnswers = List.copyOf(gapAnswers);
    }
}
