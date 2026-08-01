package com.wevo.backend.ai.service;

import com.wevo.backend.ai.context.ProjectFlowReviewContext;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.dto.model.ProjectFlowReviewOutput;

public record ProjectFlowReviewPersistCommand(
        AiJob job,
        ProjectFlowReviewContext context,
        ProjectFlowReviewOutput output
) {
    public ProjectFlowReviewPersistCommand {
        if (job == null || context == null || output == null) {
            throw new IllegalArgumentException("flow review 저장 입력은 필수입니다.");
        }
    }
}
