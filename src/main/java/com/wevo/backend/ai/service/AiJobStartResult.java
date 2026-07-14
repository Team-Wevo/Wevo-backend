package com.wevo.backend.ai.service;

import com.wevo.backend.ai.domain.AiJobStatus;
import java.util.UUID;

public record AiJobStartResult(UUID requestId, AiJobStatus status, boolean claimed) {
}
