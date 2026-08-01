package com.wevo.backend.ai.dto.model;

import java.util.List;

/** 모든 eligible 의견을 정확히 한 번 포함하는 AI 전용 partition 출력. */
public record OpinionClusteringOutput(List<OpinionClusterOutput> clusters) {
    public OpinionClusteringOutput {
        clusters = clusters == null ? null : List.copyOf(clusters);
    }
}
