package com.wevo.backend.ai.service;

import java.util.UUID;

public record AiUsageHandle(Long logId, UUID requestId) {
}
