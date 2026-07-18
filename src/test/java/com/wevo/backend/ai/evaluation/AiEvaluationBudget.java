package com.wevo.backend.ai.evaluation;

import java.math.BigDecimal;
import java.time.Duration;

public record AiEvaluationBudget(
        int maxFixtures,
        int maxProviderRequests,
        long maxOutputTokens,
        Duration deadline,
        BigDecimal maxEstimatedCost
) {

    public AiEvaluationBudget {
        if (maxFixtures <= 0 || maxProviderRequests <= 0 || maxOutputTokens <= 0) {
            throw new IllegalArgumentException("fixture, Provider 요청, 출력 token budget은 0보다 커야 합니다.");
        }
        if (deadline == null || deadline.isZero() || deadline.isNegative()) {
            throw new IllegalArgumentException("evaluation deadline은 0보다 커야 합니다.");
        }
        if (maxEstimatedCost != null && maxEstimatedCost.signum() <= 0) {
            throw new IllegalArgumentException("비용 budget은 설정할 경우 0보다 커야 합니다.");
        }
    }
}
