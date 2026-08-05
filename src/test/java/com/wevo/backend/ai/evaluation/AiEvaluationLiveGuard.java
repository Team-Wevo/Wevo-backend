package com.wevo.backend.ai.evaluation;

import java.util.List;
import java.util.function.Function;

public final class AiEvaluationLiveGuard {

    private final Function<String, String> environment;

    public AiEvaluationLiveGuard(Function<String, String> environment) {
        this.environment = environment;
    }

    public void assertAllowed(List<AiEvaluationFixture> fixtures) {
        assertAllowed("nvidia", fixtures);
    }

    public void assertAllowed(String providerId, List<AiEvaluationFixture> fixtures) {
        String normalizedProvider = normalizeProvider(providerId);
        String prefix = normalizedProvider.toUpperCase(java.util.Locale.ROOT);
        if (!hasText(environment.apply(prefix + "_API_KEY"))
                || !"true".equalsIgnoreCase(environment.apply(prefix + "_EVALUATION_ENABLED"))) {
            throw new IllegalStateException(
                    prefix + " live evaluation은 API key와 전용 opt-in이 모두 필요합니다."
            );
        }
        if (fixtures == null || fixtures.isEmpty() || fixtures.stream().anyMatch(fixture -> !fixture.metadata().synthetic())) {
            throw new IllegalArgumentException(prefix + " live evaluation에는 synthetic fixture만 사용할 수 있습니다.");
        }
    }

    private String normalizeProvider(String providerId) {
        if (providerId == null || !providerId.matches("[a-z][a-z0-9-]{0,29}")) {
            throw new IllegalArgumentException("유효한 evaluation provider ID가 필요합니다.");
        }
        return providerId;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
