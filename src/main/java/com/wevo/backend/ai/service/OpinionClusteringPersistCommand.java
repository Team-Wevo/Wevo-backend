package com.wevo.backend.ai.service;

import com.wevo.backend.ai.context.OpinionClusteringContext;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.dto.model.OpinionClusteringOutput;

public record OpinionClusteringPersistCommand(
        AiJob job,
        OpinionClusteringContext context,
        OpinionClusteringOutput output
) {
    public OpinionClusteringPersistCommand {
        if (job == null || context == null || output == null) {
            throw new IllegalArgumentException("clustering 저장 명령 값은 필수입니다.");
        }
    }
}
