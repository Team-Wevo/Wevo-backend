package com.wevo.backend.ai.context;

import com.wevo.backend.ai.domain.AiFeature;

/** 원문을 노출하지 않고 Provider 호출 전에 입력 예산 초과를 알린다. */
public class AiInputBudgetExceededException extends IllegalStateException {

    private final AiFeature feature;
    private final long estimatedInputTokens;
    private final int maxInputTokens;

    public AiInputBudgetExceededException(
            AiFeature feature,
            long estimatedInputTokens,
            int maxInputTokens
    ) {
        super("AI Provider 입력이 기능별 token budget을 초과했습니다.");
        this.feature = feature;
        this.estimatedInputTokens = estimatedInputTokens;
        this.maxInputTokens = maxInputTokens;
    }

    public AiFeature getFeature() {
        return feature;
    }

    public long getEstimatedInputTokens() {
        return estimatedInputTokens;
    }

    public int getMaxInputTokens() {
        return maxInputTokens;
    }
}
