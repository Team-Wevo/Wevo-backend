package com.wevo.backend.ai.context;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** AI-07이 부분 호출과 최종 병합에 사용할 전체 의견 coverage 계획. */
public record ContextChunkPlan(
        List<ContextChunk> chunks,
        List<Long> eligibleOpinionIds,
        List<Long> coveredOpinionIds,
        boolean complete
) {

    public ContextChunkPlan {
        chunks = List.copyOf(chunks);
        eligibleOpinionIds = List.copyOf(eligibleOpinionIds);
        coveredOpinionIds = List.copyOf(coveredOpinionIds);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("context chunk 계획에는 하나 이상의 chunk가 필요합니다.");
        }
        for (int index = 0; index < chunks.size(); index++) {
            if (chunks.get(index).index() != index + 1) {
                throw new IllegalArgumentException("context chunk index는 1부터 연속되어야 합니다.");
            }
        }
        List<Long> actualCovered = chunks.stream()
                .flatMap(chunk -> chunk.opinionIds().stream())
                .toList();
        Set<Long> eligible = new HashSet<>(eligibleOpinionIds);
        Set<Long> covered = new HashSet<>(coveredOpinionIds);
        boolean exactCoverage = eligible.size() == eligibleOpinionIds.size()
                && covered.size() == coveredOpinionIds.size()
                && eligible.equals(covered)
                && actualCovered.equals(coveredOpinionIds);
        if (!complete || !exactCoverage) {
            throw new IncompleteContextCoverageException();
        }
    }
}
