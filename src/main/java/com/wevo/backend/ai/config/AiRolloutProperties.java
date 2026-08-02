package com.wevo.backend.ai.config;

import com.wevo.backend.ai.domain.AiFeature;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 평가가 끝난 model/prompt/policy 조합과 기능별 canary route. */
@ConfigurationProperties(prefix = "wevo.ai.rollout")
public record AiRolloutProperties(
        Boolean enabled,
        Map<String, Combination> combinations,
        Map<String, Route> routes
) {

    public AiRolloutProperties {
        enabled = enabled != null && enabled;
        combinations = combinations == null ? Map.of() : Map.copyOf(combinations);
        routes = routes == null ? Map.of() : Map.copyOf(routes);
        combinations.forEach(AiRolloutProperties::validateCombination);
        Set<String> supported = new HashSet<>();
        for (AiFeature feature : AiFeature.values()) {
            supported.add(feature.configKey());
        }
        if (!supported.containsAll(routes.keySet())) {
            throw new IllegalArgumentException("wevo.ai.rollout.routes에 지원하지 않는 기능 key가 있습니다.");
        }
        Map<String, Combination> configuredCombinations = combinations;
        routes.forEach((key, route) -> validateRoute(key, route, configuredCombinations));
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    private static void validateCombination(String id, Combination combination) {
        requireText(id, "rollout combination ID");
        if (combination == null) {
            throw new IllegalArgumentException("rollout combination 설정은 필수입니다.");
        }
        requireText(combination.modelId(), "model-id");
        requireText(combination.promptVersion(), "prompt-version");
        requireText(combination.schemaVersion(), "schema-version");
        requireText(combination.reasoningEffort(), "reasoning-effort");
        requireText(combination.pricingVersion(), "pricing-version");
        requireText(combination.policyVersion(), "policy-version");
    }

    private static void validateRoute(String key, Route route, Map<String, Combination> combinations) {
        if (route == null || !combinations.containsKey(route.baseline())) {
            throw new IllegalArgumentException("rollout baseline combination을 찾을 수 없습니다: " + key);
        }
        if (route.candidate() != null && !route.candidate().isBlank()
                && !combinations.containsKey(route.candidate())) {
            throw new IllegalArgumentException("rollout candidate combination을 찾을 수 없습니다: " + key);
        }
        int percent = route.canaryPercent() == null ? 0 : route.canaryPercent();
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("rollout canary-percent는 0 이상 100 이하여야 합니다.");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 100) {
            throw new IllegalArgumentException(field + "는 1자 이상 100자 이하여야 합니다.");
        }
    }

    public record Combination(
            String modelId,
            String promptVersion,
            String schemaVersion,
            String reasoningEffort,
            String pricingVersion,
            String policyVersion,
            Boolean evaluated,
            Boolean humanReviewed
    ) {
        public boolean approved() {
            return Boolean.TRUE.equals(evaluated) && Boolean.TRUE.equals(humanReviewed);
        }
    }

    public record Route(
            String baseline,
            String candidate,
            Integer canaryPercent,
            Set<Long> syntheticProjectIds
    ) {
        public Route {
            canaryPercent = canaryPercent == null ? 0 : canaryPercent;
            syntheticProjectIds = syntheticProjectIds == null ? Set.of() : Set.copyOf(syntheticProjectIds);
            if (syntheticProjectIds.stream().anyMatch(id -> id == null || id <= 0)) {
                throw new IllegalArgumentException("synthetic-project-ids는 1 이상의 ID여야 합니다.");
            }
        }
    }
}
