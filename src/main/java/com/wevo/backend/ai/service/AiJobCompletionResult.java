package com.wevo.backend.ai.service;

import com.wevo.backend.ai.domain.AiJobStatus;
import java.util.UUID;

public record AiJobCompletionResult(
        UUID requestId,
        AiJobStatus status,
        Long resultId,
        boolean resultPersisted
) {
}
