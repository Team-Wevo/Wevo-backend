package com.wevo.backend.ai.service;

import com.wevo.backend.ai.domain.AiJob;

import java.util.UUID;

public record AiUsageHandle(Long logId, UUID requestId, AiJob aiJob) {

    public AiUsageHandle(Long logId, UUID requestId) {
        this(logId, requestId, null);
    }
}
