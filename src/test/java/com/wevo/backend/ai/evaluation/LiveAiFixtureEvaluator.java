package com.wevo.backend.ai.evaluation;

import com.wevo.backend.ai.client.AiProviderGateway;
import com.wevo.backend.ai.client.AiUsageMetadata;
import com.wevo.backend.ai.client.StructuredAiProviderResponse;
import com.wevo.backend.ai.domain.AiCostSnapshot;
import com.wevo.backend.ai.domain.AiErrorType;
import com.wevo.backend.ai.exception.AiProviderException;
import com.wevo.backend.ai.service.AiCostCalculator;
import com.wevo.backend.ai.service.AiErrorClassifier;

import java.util.List;

public final class LiveAiFixtureEvaluator<T> implements AiFixtureEvaluator {

    private final AiProviderGateway gateway;
    private final LiveEvaluationCase<T> evaluationCase;
    private final AiCostCalculator costCalculator;
    private final AiErrorClassifier errorClassifier;

    public LiveAiFixtureEvaluator(
            AiProviderGateway gateway,
            LiveEvaluationCase<T> evaluationCase,
            AiCostCalculator costCalculator,
            AiErrorClassifier errorClassifier
    ) {
        this.gateway = gateway;
        this.evaluationCase = evaluationCase;
        this.costCalculator = costCalculator;
        this.errorClassifier = errorClassifier;
    }

    @Override
    public AiEvaluationObservation evaluate(AiEvaluationFixture fixture) {
        try {
            StructuredAiProviderResponse<T> response = gateway.generateStructured(
                    evaluationCase.requestFor(fixture)
            );
            AiCostSnapshot cost = costCalculator.calculate(response.usageMetadata());
            return AiEvaluationObservation.success(
                    evaluationCase.normalize(fixture, response.result()),
                    response.usageMetadata(),
                    cost,
                    response.attemptCount()
            );
        } catch (AiProviderException exception) {
            AiUsageMetadata usage = exception.getUsageMetadata();
            AiCostSnapshot cost = usage == null ? null : costCalculator.calculate(usage);
            return AiEvaluationObservation.failure(
                    map(errorClassifier.classify(exception)),
                    usage,
                    cost,
                    exception.getAttemptCount(),
                    List.of()
            );
        }
    }

    private AiEvaluationOutcome map(AiErrorType errorType) {
        return switch (errorType) {
            case INVALID_RESPONSE -> AiEvaluationOutcome.EMPTY_RESPONSE;
            case PROVIDER_REFUSAL -> AiEvaluationOutcome.REFUSAL;
            case PROVIDER_MAX_TOKENS -> AiEvaluationOutcome.MAX_TOKENS;
            case JSON_PARSE_FAILED -> AiEvaluationOutcome.JSON_PARSE_FAILURE;
            case SCHEMA_VALIDATION_FAILED -> AiEvaluationOutcome.SCHEMA_VALIDATION_FAILURE;
            case TYPE_CONVERSION_FAILED -> AiEvaluationOutcome.TYPE_CONVERSION_FAILURE;
            case SEMANTIC_VALIDATION_FAILED -> AiEvaluationOutcome.SEMANTIC_VALIDATION_FAILURE;
            default -> AiEvaluationOutcome.PROVIDER_FAILURE;
        };
    }
}
