package com.wevo.backend.ai.context;

import com.wevo.backend.ai.domain.AiFeature;

/** versioned 추정 정책으로 계산한 최종 Provider 입력 예산 결과. */
public record AiTokenBudgetEstimate(
        AiFeature feature,
        String policyVersion,
        long estimatedInputTokens,
        int maxInputTokens,
        boolean withinBudget
) {
}
