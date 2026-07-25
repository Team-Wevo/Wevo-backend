package com.wevo.backend.ai.context;

import java.util.List;

/** 현재 synthesis 식별 정보와 GAP 보충 근거. */
public record AiSynthesisContext(
        Long synthesisSetId,
        long opinionGateGeneration,
        String consensusSummary,
        List<AiGapAnswerContext> gapAnswers
) {

    public AiSynthesisContext {
        gapAnswers = List.copyOf(gapAnswers);
    }
}
