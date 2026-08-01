package com.wevo.backend.ai.dto.model;

import java.util.List;

/** AI 전용 의견 cluster 출력 항목. API DTO·영속 entity와 분리한다. */
public record OpinionClusterOutput(
        int order,
        String title,
        String summary,
        List<Long> opinionIds
) {
    public OpinionClusterOutput {
        opinionIds = opinionIds == null ? null : List.copyOf(opinionIds);
    }
}
