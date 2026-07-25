package com.wevo.backend.ai.context;

import java.util.List;

/** 의견 중간 절단 없이 하나의 Provider 입력에 포함할 결정적 chunk. */
public record ContextChunk(
        int index,
        List<AiOpinionContext> opinions,
        List<Long> opinionIds,
        long estimatedInputTokens
) {

    public ContextChunk {
        opinions = List.copyOf(opinions);
        opinionIds = List.copyOf(opinionIds);
        if (index <= 0 || estimatedInputTokens < 0) {
            throw new IllegalArgumentException("chunk index와 추정 token 수가 유효하지 않습니다.");
        }
        List<Long> actualIds = opinions.stream().map(AiOpinionContext::opinionId).toList();
        if (!actualIds.equals(opinionIds)) {
            throw new IllegalArgumentException("chunk opinion과 opinion ID 목록이 일치해야 합니다.");
        }
    }
}
