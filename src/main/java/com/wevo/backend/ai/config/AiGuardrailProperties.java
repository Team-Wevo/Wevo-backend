package com.wevo.backend.ai.config;

import com.wevo.backend.ai.domain.AiFeature;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * AI 사용자 요청 quota와 프로젝트 비용 예약 정책.
 *
 * <p>입출력 token 상한은 {@link AiProperties}가 정본이며, 이 설정은 요청 횟수와 한 job에서
 * 발생할 수 있는 Provider 호출 횟수 및 프로젝트 비용 예산만 정의한다.</p>
 */
@ConfigurationProperties(prefix = "wevo.ai.guardrails")
public record AiGuardrailProperties(
        Boolean enabled,
        String policyVersion,
        QuotaLimits user,
        QuotaLimits project,
        Map<String, FeatureLimits> features,
        CostLimits cost
) {

    private static final int MAX_REQUEST_LIMIT = 10_000_000;
    private static final BigDecimal MAX_COST_USD = new BigDecimal("1000000");

    public AiGuardrailProperties {
        enabled = enabled != null && enabled;
        features = features == null ? Map.of() : Map.copyOf(features);
        if (enabled) {
            if (!StringUtils.hasText(policyVersion) || policyVersion.length() > 100) {
                throw new IllegalArgumentException(
                        "wevo.ai.guardrails.policy-version은 1자 이상 100자 이하여야 합니다.");
            }
            user = requireQuota("wevo.ai.guardrails.user", user);
            project = requireQuota("wevo.ai.guardrails.project", project);

            Set<String> requiredKeys = new HashSet<>();
            for (AiFeature feature : AiFeature.values()) {
                requiredKeys.add(feature.configKey());
            }
            if (!features.keySet().equals(requiredKeys)) {
                throw new IllegalArgumentException(
                        "wevo.ai.guardrails.features에는 모든 AI 기능을 정확히 한 번씩 설정해야 합니다.");
            }
            features.forEach((key, limits) -> requireFeature(
                    "wevo.ai.guardrails.features." + key, limits));
            cost = requireCost(cost);
        }
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public FeatureLimits limitsFor(AiFeature feature) {
        FeatureLimits limits = features.get(feature.configKey());
        if (isEnabled() && limits == null) {
            throw new IllegalStateException("AI 기능별 guardrail 설정을 찾을 수 없습니다.");
        }
        return limits;
    }

    private static QuotaLimits requireQuota(String path, QuotaLimits limits) {
        if (limits == null) {
            throw new IllegalArgumentException(path + " 설정은 필수입니다.");
        }
        validateLimit(path + ".per-minute", limits.perMinute());
        validateLimit(path + ".per-day", limits.perDay());
        if (limits.perDay() < limits.perMinute()) {
            throw new IllegalArgumentException(path + ".per-day는 per-minute 이상이어야 합니다.");
        }
        return limits;
    }

    private static void requireFeature(String path, FeatureLimits limits) {
        if (limits == null) {
            throw new IllegalArgumentException(path + " 설정은 필수입니다.");
        }
        requireQuota(path, new QuotaLimits(limits.perMinute(), limits.perDay()));
        validateLimit(path + ".max-provider-requests", limits.maxProviderRequests());
    }

    private static CostLimits requireCost(CostLimits limits) {
        if (limits == null) {
            throw new IllegalArgumentException("wevo.ai.guardrails.cost 설정은 필수입니다.");
        }
        validateCost("daily-budget-usd", limits.dailyBudgetUsd());
        validateCost("monthly-budget-usd", limits.monthlyBudgetUsd());
        validateCost("max-estimated-job-cost-usd", limits.maxEstimatedJobCostUsd());
        if (limits.monthlyBudgetUsd().compareTo(limits.dailyBudgetUsd()) < 0) {
            throw new IllegalArgumentException("월간 AI 비용 예산은 일일 예산 이상이어야 합니다.");
        }
        if (limits.dailyBudgetUsd().compareTo(limits.maxEstimatedJobCostUsd()) < 0) {
            throw new IllegalArgumentException("일일 AI 비용 예산은 단일 job 최대 예상 비용 이상이어야 합니다.");
        }
        return limits;
    }

    private static void validateLimit(String path, Integer value) {
        if (value == null || value <= 0 || value > MAX_REQUEST_LIMIT) {
            throw new IllegalArgumentException(path + "는 1 이상 10000000 이하여야 합니다.");
        }
    }

    private static void validateCost(String key, BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.compareTo(MAX_COST_USD) > 0
                || value.scale() > 6) {
            throw new IllegalArgumentException(
                    "wevo.ai.guardrails.cost." + key + "는 소수 6자리 이하의 0 초과 USD 값이어야 합니다.");
        }
    }

    public record QuotaLimits(Integer perMinute, Integer perDay) {
    }

    public record FeatureLimits(Integer perMinute, Integer perDay, Integer maxProviderRequests) {
    }

    public record CostLimits(
            BigDecimal dailyBudgetUsd,
            BigDecimal monthlyBudgetUsd,
            BigDecimal maxEstimatedJobCostUsd
    ) {
    }
}
