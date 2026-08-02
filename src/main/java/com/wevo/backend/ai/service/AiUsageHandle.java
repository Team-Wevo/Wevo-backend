package com.wevo.backend.ai.service;

import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiFeature;

import java.time.LocalDateTime;

import java.util.UUID;

public record AiUsageHandle(
        Long logId,
        UUID requestId,
        AiJob aiJob,
        AiFeature feature,
        String provider,
        String modelId,
        String promptVersion,
        String schemaVersion,
        LocalDateTime startedAt
) {

    public AiUsageHandle(Long logId, UUID requestId, AiJob aiJob) {
        this(logId, requestId, aiJob,
                aiJob == null ? null : aiJob.getFeature(), null,
                aiJob == null ? null : aiJob.getModelId(),
                aiJob == null ? null : aiJob.getPromptVersion(),
                aiJob == null ? null : aiJob.getSchemaVersion(), null);
    }

    public AiUsageHandle(Long logId, UUID requestId) {
        this(logId, requestId, null, null, null, null, null, null, null);
    }
}
