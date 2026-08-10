package com.wevo.backend.ai.evaluation;

import com.wevo.backend.ai.client.AiProviderGateway;
import com.wevo.backend.ai.client.AiProviderRequest;
import com.wevo.backend.ai.client.AiProviderResponse;
import com.wevo.backend.ai.client.AiUsageMetadata;
import com.wevo.backend.ai.client.OutputSchemaId;
import com.wevo.backend.ai.client.StructuredAiProviderRequest;
import com.wevo.backend.ai.client.StructuredAiProviderResponse;
import com.wevo.backend.ai.client.StructuredOutputDefinition;
import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.config.AiPricingProperties;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.exception.AiProviderException;
import com.wevo.backend.ai.prompt.PromptTemplateId;
import com.wevo.backend.ai.prompt.RenderedPrompt;
import com.wevo.backend.ai.service.AiCostCalculator;
import com.wevo.backend.ai.service.AiErrorClassifier;
import com.wevo.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LiveAiFixtureEvaluatorTest {

    private final AiEvaluationFixture fixture = new AiEvaluationFixtureLoader().load(
            "ai/evaluation/issue-detection/all-agreed-proposal.json"
    );
    private final LiveEvaluationCase<TestOutput> evaluationCase = new TestEvaluationCase();
    private final AiCostCalculator costCalculator = new AiCostCalculator(
            new AiPricingProperties("test", Map.of())
    );

    @Test
    void normalizesTypedGatewayResultWithoutProviderSdkDependency() {
        AiUsageMetadata usage = new AiUsageMetadata(
                "test-provider", "request-1", "model", 10L, 2L, null, null
        );
        AiProviderGateway gateway = new StubGateway(
                new StructuredAiProviderResponse<>(
                        new TestOutput("요약"),
                        new PromptTemplateId("evaluation-test", 1),
                        new OutputSchemaId("evaluation-test", 1),
                        usage,
                        "stop",
                        2
                ),
                null
        );
        LiveAiFixtureEvaluator<TestOutput> evaluator = new LiveAiFixtureEvaluator<>(
                gateway, evaluationCase, costCalculator, new AiErrorClassifier()
        );

        AiEvaluationObservation observation = evaluator.evaluate(fixture);

        assertThat(observation.outcome()).isEqualTo(AiEvaluationOutcome.SUCCESS);
        assertThat(observation.attemptCount()).isEqualTo(2);
        assertThat(observation.candidate().claims()).hasSize(1);
        assertThat(observation.cost().estimatedCost()).isNull();
    }

    @Test
    void mapsCommonStructuredFailureToNormalizedReasonWithoutRawProviderBody() {
        AiUsageMetadata usage = new AiUsageMetadata(
                "test-provider", "request-1", "model", 10L, 2L, null, null
        );
        AiProviderException exception = new AiProviderException(
                ErrorCode.AI_STRUCTURED_OUTPUT_SCHEMA_VALIDATION_FAILED,
                new IllegalStateException("nvapi-secret-provider-body"),
                usage,
                3
        );
        LiveAiFixtureEvaluator<TestOutput> evaluator = new LiveAiFixtureEvaluator<>(
                new StubGateway(null, exception),
                evaluationCase,
                costCalculator,
                new AiErrorClassifier()
        );

        AiEvaluationObservation observation = evaluator.evaluate(fixture);

        assertThat(observation.outcome()).isEqualTo(AiEvaluationOutcome.SCHEMA_VALIDATION_FAILURE);
        assertThat(observation.attemptCount()).isEqualTo(3);
        assertThat(observation.candidate()).isNull();
        assertThat(observation.toString()).doesNotContain("nvapi-secret-provider-body");
    }

    private record TestOutput(String summary) {
    }

    private static final class TestEvaluationCase implements LiveEvaluationCase<TestOutput> {

        @Override
        public StructuredAiProviderRequest<TestOutput> requestFor(AiEvaluationFixture fixture) {
            return new StructuredAiProviderRequest<>(
                    AiFeature.DRAFT_REVIEW,
                    new RenderedPrompt(
                            new PromptTemplateId("evaluation-test", 1),
                            "Return JSON.",
                            "Synthetic fixture: " + fixture.metadata().id()
                    ),
                    StructuredOutputDefinition.of(
                            new OutputSchemaId("evaluation-test", 1),
                            TestOutput.class
                    ),
                    StructuredOutputValidationContext.empty()
            );
        }

        @Override
        public AiEvaluationCandidate normalize(AiEvaluationFixture fixture, TestOutput result) {
            return new AiEvaluationCandidate(
                    List.of(),
                    List.of(new AiEvaluationCandidate.Claim(result.summary(), false, java.util.Set.of())),
                    java.util.Set.of(),
                    0,
                    0
            );
        }
    }

    private static final class StubGateway implements AiProviderGateway {

        private final StructuredAiProviderResponse<?> response;
        private final AiProviderException failure;

        private StubGateway(StructuredAiProviderResponse<?> response, AiProviderException failure) {
            this.response = response;
            this.failure = failure;
        }

        @Override
        public AiProviderResponse generate(AiProviderRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> StructuredAiProviderResponse<T> generateStructured(StructuredAiProviderRequest<T> request) {
            if (failure != null) {
                throw failure;
            }
            return (StructuredAiProviderResponse<T>) response;
        }
    }
}
