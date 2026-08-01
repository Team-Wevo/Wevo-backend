package com.wevo.backend.ai.service;

import com.wevo.backend.ai.context.AiOpinionContext;
import com.wevo.backend.ai.context.AiTemplateContext;
import com.wevo.backend.ai.dto.model.OpinionClusteringOutput;
import java.util.List;

/** 부분 분류와 계층 병합이 공유하는 prompt payload. */
public record OpinionClusteringPromptContext(
        String mode,
        Long projectId,
        Long sectionId,
        String sectionTitle,
        AiTemplateContext template,
        List<AiOpinionContext> opinions,
        List<PartialClustering> partials
) {
    public static final String PARTIAL = "PARTIAL";
    public static final String FINAL_MERGE = "FINAL_MERGE";

    public OpinionClusteringPromptContext {
        opinions = opinions == null ? null : List.copyOf(opinions);
        partials = partials == null ? null : List.copyOf(partials);
    }

    public record PartialClustering(
            int chunkIndex,
            List<Long> opinionIds,
            OpinionClusteringOutput output
    ) {
        public PartialClustering {
            opinionIds = List.copyOf(opinionIds);
        }
    }
}
