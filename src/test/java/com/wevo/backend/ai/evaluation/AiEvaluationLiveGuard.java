package com.wevo.backend.ai.evaluation;

import java.util.List;
import java.util.function.Function;

public final class AiEvaluationLiveGuard {

    private final Function<String, String> environment;

    public AiEvaluationLiveGuard(Function<String, String> environment) {
        this.environment = environment;
    }

    public void assertAllowed(List<AiEvaluationFixture> fixtures) {
        if (!hasText(environment.apply("NVIDIA_API_KEY"))
                || !"true".equalsIgnoreCase(environment.apply("NVIDIA_EVALUATION_ENABLED"))) {
            throw new IllegalStateException("NVIDIA live evaluation은 API key와 전용 opt-in이 모두 필요합니다.");
        }
        if (fixtures == null || fixtures.isEmpty() || fixtures.stream().anyMatch(fixture -> !fixture.metadata().synthetic())) {
            throw new IllegalArgumentException("NVIDIA live evaluation에는 synthetic fixture만 사용할 수 있습니다.");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
