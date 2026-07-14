package com.wevo.backend.ai.service;

import java.util.UUID;

public record AiInvocationResult<T>(T value, Long usageLogId, UUID requestId) {
}
