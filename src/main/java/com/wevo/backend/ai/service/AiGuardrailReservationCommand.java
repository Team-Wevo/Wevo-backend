package com.wevo.backend.ai.service;

import com.wevo.backend.ai.domain.AiFeature;

public record AiGuardrailReservationCommand(
        java.util.UUID requestId,
        String idempotencyKey,
        Integer executionSequence,
        Long projectId,
        Long userId,
        AiFeature feature,
        String modelId,
        Integer maxOutputTokens
) {

    public AiGuardrailReservationCommand {
        if (requestId == null
                || idempotencyKey == null || !idempotencyKey.matches("[0-9a-f]{64}")
                || executionSequence == null || executionSequence <= 0
                || projectId == null || projectId <= 0 || userId == null || userId <= 0
                || feature == null || modelId == null || modelId.isBlank()
                || maxOutputTokens == null || maxOutputTokens <= 0) {
            throw new IllegalArgumentException("AI guardrail 예약 식별자와 실행 정책은 필수입니다.");
        }
    }
}
