package com.wevo.backend.ai.evaluation;

import java.math.BigDecimal;
import java.time.Duration;

public record AiEvaluationBudget(
        int maxFixtures,
        int maxProviderRequests,
        int maxProviderRequestsPerFixture,
        long maxOutputTokens,
        long maxOutputTokensPerFixture,
        Duration deadline,
        BigDecimal maxEstimatedCost,
        BigDecimal maxEstimatedCostPerFixture
) {

    public AiEvaluationBudget {
        if (maxFixtures <= 0 || maxProviderRequests <= 0
                || maxProviderRequestsPerFixture <= 0
                || maxProviderRequestsPerFixture > maxProviderRequests
                || maxOutputTokens <= 0
                || maxOutputTokensPerFixture <= 0
                || maxOutputTokensPerFixture > maxOutputTokens) {
            throw new IllegalArgumentException("fixture, Provider 요청, 출력 token budget은 0보다 커야 합니다.");
        }
        if (deadline == null || deadline.isZero() || deadline.isNegative()) {
            throw new IllegalArgumentException("evaluation deadline은 0보다 커야 합니다.");
        }
        if (maxEstimatedCost != null && maxEstimatedCost.signum() <= 0) {
            throw new IllegalArgumentException("비용 budget은 설정할 경우 0보다 커야 합니다.");
        }
        if (maxEstimatedCostPerFixture != null
                && (maxEstimatedCost == null
                || maxEstimatedCostPerFixture.signum() <= 0
                || maxEstimatedCostPerFixture.compareTo(maxEstimatedCost) > 0)) {
            throw new IllegalArgumentException("fixture 예상 비용은 전체 비용 budget 이하여야 합니다.");
        }
    }

    public AiEvaluationBudget(
            int maxFixtures,
            int maxProviderRequests,
            long maxOutputTokens,
            Duration deadline,
            BigDecimal maxEstimatedCost
    ) {
        this(
                maxFixtures, maxProviderRequests, 1, maxOutputTokens, 1,
                deadline, maxEstimatedCost, null
        );
    }
}
