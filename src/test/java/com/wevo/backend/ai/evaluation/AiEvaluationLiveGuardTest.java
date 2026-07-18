package com.wevo.backend.ai.evaluation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiEvaluationLiveGuardTest {

    private final AiEvaluationFixture synthetic = new AiEvaluationFixtureLoader().load(
            "ai/evaluation/issue-detection/all-agreed-proposal.json"
    );

    @Test
    void requiresEvaluationSpecificOptInInAdditionToApiKey() {
        AiEvaluationLiveGuard missingOptIn = guard(Map.of(
                "NVIDIA_API_KEY", "nvapi-secret",
                "NVIDIA_INTEGRATION_ENABLED", "true"
        ));

        assertThatThrownBy(() -> missingOptIn.assertAllowed(List.of(synthetic)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("nvapi-secret");

        AiEvaluationLiveGuard enabled = guard(Map.of(
                "NVIDIA_API_KEY", "nvapi-secret",
                "NVIDIA_EVALUATION_ENABLED", "true"
        ));
        assertThatCode(() -> enabled.assertAllowed(List.of(synthetic))).doesNotThrowAnyException();
    }

    @Test
    void rejectsNonSyntheticFixtureEvenWhenOptedIn() {
        AiEvaluationFixture.Metadata metadata = new AiEvaluationFixture.Metadata(
                synthetic.metadata().id(),
                synthetic.metadata().feature(),
                synthetic.metadata().description(),
                synthetic.metadata().language(),
                synthetic.metadata().tags(),
                synthetic.metadata().datasetVersion(),
                synthetic.metadata().kind(),
                false
        );
        AiEvaluationFixture nonSynthetic = new AiEvaluationFixture(
                synthetic.schemaVersion(), metadata, synthetic.input(), synthetic.expected()
        );

        assertThatThrownBy(() -> guard(Map.of(
                "NVIDIA_API_KEY", "nvapi-secret",
                "NVIDIA_EVALUATION_ENABLED", "true"
        )).assertAllowed(List.of(nonSynthetic)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("synthetic");
    }

    private AiEvaluationLiveGuard guard(Map<String, String> environment) {
        return new AiEvaluationLiveGuard(environment::get);
    }
}
