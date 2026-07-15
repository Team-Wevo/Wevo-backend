package com.wevo.backend.ai.service;

import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import java.util.UUID;

public record AiJobCreateResult(
        UUID requestId,
        AiJobStatus status,
        Long resultId,
        int executionSequence,
        boolean created,
        boolean resultReused
) {

    public static AiJobCreateResult from(AiJob job, boolean created) {
        return new AiJobCreateResult(
                job.getRequestId(),
                job.getStatus(),
                job.getResultId(),
                job.getExecutionSequence(),
                created,
                !created && job.getStatus() == AiJobStatus.SUCCEEDED
        );
    }
}
