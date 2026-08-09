package com.wevo.backend.ai.client;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.context.AiTokenBudgetEstimator;
import com.wevo.backend.ai.context.AiTokenBudgetInput;
import com.wevo.backend.ai.exception.AiExceptionTranslator;
import com.wevo.backend.ai.exception.AiProviderException;
import com.wevo.backend.global.exception.ErrorCode;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class AbstractSpringAiGateway implements AiProviderGateway {

    private final ChatClient chatClient;
    private final AiProperties properties;
    private final AiProviderRuntimeOptions runtimeOptions;
    private final AiExceptionTranslator exceptionTranslator;
    private final ExecutorService providerRequestExecutor;
    private final AiUsageExtractor usageExtractor;
    private final AiRetrySleeper retrySleeper;
    private final AiTokenBudgetEstimator tokenBudgetEstimator;

    protected AbstractSpringAiGateway(
            ChatClient chatClient,
            AiProperties properties,
            AiProviderRuntimeOptions runtimeOptions,
            AiExceptionTranslator exceptionTranslator,
            ExecutorService providerRequestExecutor,
            AiUsageExtractor usageExtractor,
            AiRetrySleeper retrySleeper,
            AiTokenBudgetEstimator tokenBudgetEstimator
    ) {
        this.chatClient = chatClient;
        this.properties = properties;
        this.runtimeOptions = runtimeOptions;
        this.exceptionTranslator = exceptionTranslator;
        this.providerRequestExecutor = providerRequestExecutor;
        this.usageExtractor = usageExtractor;
        this.retrySleeper = retrySleeper;
        this.tokenBudgetEstimator = tokenBudgetEstimator;
    }

    @Override
    public AiProviderResponse generate(AiProviderRequest request) {
        AiProperties.ModelOptions options = options(request.feature(), request.executionPolicy());
        int attempts = 0;

        while (true) {
            attempts++;
            try {
                AiProviderResponse response = generateOnce(request, options);
                return new AiProviderResponse(
                        response.content(),
                        response.usageMetadata(),
                        response.finishReason(),
                        attempts
                );
            } catch (AiProviderException exception) {
                AiProviderException contextual = exception.withAttemptContext(attempts);
                if (attempts > options.maxRetries() || !isRetryable(contextual.getErrorCode())) {
                    throw contextual;
                }
                sleepBeforeRetry(options, attempts, contextual);
            }
        }
    }

    @Override
    public <T> StructuredAiProviderResponse<T> generateStructured(StructuredAiProviderRequest<T> request) {
        AiProperties.ModelOptions options = options(request.feature(), request.executionPolicy());
        UsageAccumulator usageAccumulator = new UsageAccumulator();
        AttemptCounter attemptCounter = new AttemptCounter();
        StructuredConversionFailure previousFailure = null;
        int maxCorrectionRetries = properties.structuredOutput().maxCorrectionRetries();

        for (int correctionAttempt = 0; correctionAttempt <= maxCorrectionRetries; correctionAttempt++) {
            String userPrompt = StructuredPromptFormatter.userPrompt(request, previousFailure);
            ProviderStructuredResponse<T> providerResponse = invokeStructuredWithProviderRetry(
                    request,
                    userPrompt,
                    options,
                    usageAccumulator,
                    attemptCounter
            );
            usageAccumulator.add(providerResponse.usageMetadata());

            ErrorCode finishError = finishReasonError(providerResponse.finishReason());
            if (finishError != null) {
                throw structuredException(finishError, usageAccumulator.snapshot(), attemptCounter.value());
            }

            StructuredConversionResult<T> conversion = providerResponse.conversion();
            if (providerResponse.contentEmpty() || conversion == null) {
                throw structuredException(
                        ErrorCode.AI_PROVIDER_INVALID_RESPONSE,
                        usageAccumulator.snapshot(),
                        attemptCounter.value()
                );
            }
            if (conversion.isSuccess()) {
                validateSemantics(request, conversion.value(), usageAccumulator.snapshot(), attemptCounter.value());
                return new StructuredAiProviderResponse<>(
                        conversion.value(),
                        request.prompt().id(),
                        request.outputDefinition().schemaId(),
                        usageAccumulator.snapshot(),
                        providerResponse.finishReason(),
                        attemptCounter.value(),
                        Math.max(0, attemptCounter.value() - correctionAttempt - 1),
                        correctionAttempt
                );
            }

            previousFailure = conversion.failure();
            if (correctionAttempt == maxCorrectionRetries) {
                throw structuredException(
                        errorCodeFor(previousFailure),
                        usageAccumulator.snapshot(),
                        attemptCounter.value()
                );
            }
        }

        throw structuredException(
                ErrorCode.AI_STRUCTURED_OUTPUT_SCHEMA_VALIDATION_FAILED,
                usageAccumulator.snapshot(),
                attemptCounter.value()
        );
    }

    private <T> ProviderStructuredResponse<T> invokeStructuredWithProviderRetry(
            StructuredAiProviderRequest<T> request,
            String userPrompt,
            AiProperties.ModelOptions options,
            UsageAccumulator usageAccumulator,
            AttemptCounter attemptCounter
    ) {
        int transportAttempts = 0;
        while (true) {
            transportAttempts++;
            attemptCounter.increment();
            try {
                return generateStructuredOnce(request, userPrompt, options);
            } catch (AiProviderException exception) {
                if (transportAttempts > options.maxRetries() || !isRetryable(exception.getErrorCode())) {
                    AiUsageMetadata usage = mergeUsage(usageAccumulator.snapshot(), exception.getUsageMetadata());
                    throw new AiProviderException(
                            exception.getErrorCode(),
                            exception.getCause(),
                            usage,
                            attemptCounter.value(),
                            exception.getRetryAfter()
                    );
                }
                try {
                    sleepBeforeRetry(options, transportAttempts, exception);
                } catch (AiProviderException interrupted) {
                    throw new AiProviderException(
                            interrupted.getErrorCode(),
                            interrupted.getCause(),
                            mergeUsage(usageAccumulator.snapshot(), interrupted.getUsageMetadata()),
                            attemptCounter.value(),
                            interrupted.getRetryAfter()
                    );
                }
            }
        }
    }

    private <T> ProviderStructuredResponse<T> generateStructuredOnce(
            StructuredAiProviderRequest<T> request,
            String userPrompt,
            AiProperties.ModelOptions options
    ) {
        tokenBudgetEstimator.requireWithinBudget(
                request.feature(),
                AiTokenBudgetInput.of(request.prompt().systemPrompt(), userPrompt)
        );
        Future<ResponseEntity<ChatResponse, StructuredConversionResult<T>>> future = providerRequestExecutor.submit(
                () -> callProviderStructured(request, userPrompt, options)
        );

        try {
            ResponseEntity<ChatResponse, StructuredConversionResult<T>> response = future.get(
                    options.timeout().toMillis(),
                    TimeUnit.MILLISECONDS
            );
            return toStructuredResponse(response, options.model());
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw exceptionTranslator.translate(exception);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw exceptionTranslator.translate(exception);
        } catch (ExecutionException exception) {
            throw exceptionTranslator.translate(exception.getCause());
        }
    }

    private <T> ResponseEntity<ChatResponse, StructuredConversionResult<T>> callProviderStructured(
            StructuredAiProviderRequest<T> request,
            String userPrompt,
            AiProperties.ModelOptions options
    ) {
        StrictStructuredOutputConverter<T> converter = new StrictStructuredOutputConverter<>(
                request.outputDefinition()
        );
        return chatClient.prompt()
                .messages(List.of(
                        new SystemMessage(request.prompt().systemPrompt()),
                        new UserMessage(userPrompt)
                ))
                .options(structuredProviderOptions(options, request, reasoning(request.executionPolicy())))
                .call()
                .responseEntity(converter);
    }

    private <T> ProviderStructuredResponse<T> toStructuredResponse(
            ResponseEntity<ChatResponse, StructuredConversionResult<T>> responseEntity,
            String requestedModel
    ) {
        if (responseEntity == null || responseEntity.response() == null) {
            throw new AiProviderException(
                    ErrorCode.AI_PROVIDER_INVALID_RESPONSE,
                    new IllegalStateException("AI Provider structured response is null")
            );
        }

        ChatResponse response = responseEntity.response();
        AiUsageMetadata usage = usageExtractor.extract(
                response.getMetadata(), requestedModel, runtimeOptions.providerId()
        );
        Generation generation = response.getResult();
        if (generation == null || generation.getOutput() == null) {
            throw new AiProviderException(
                    ErrorCode.AI_PROVIDER_INVALID_RESPONSE,
                    new IllegalStateException("AI Provider structured result is empty"),
                    usage,
                    0
            );
        }
        String finishReason = generation.getMetadata() == null
                ? null
                : generation.getMetadata().getFinishReason();
        if (hasProviderRefusal(generation)) {
            finishReason = "refusal";
        }
        boolean contentEmpty = generation.getOutput().getText() == null
                || generation.getOutput().getText().isBlank();
        return new ProviderStructuredResponse<>(responseEntity.entity(), usage, finishReason, contentEmpty);
    }

    private <T> void validateSemantics(
            StructuredAiProviderRequest<T> request,
            T value,
            AiUsageMetadata usage,
            int attempts
    ) {
        try {
            request.outputDefinition().validator().validate(value, request.validationContext());
        } catch (StructuredOutputSemanticException exception) {
            throw new AiProviderException(
                    ErrorCode.AI_STRUCTURED_OUTPUT_SEMANTIC_VALIDATION_FAILED,
                    exception,
                    usage,
                    attempts
            );
        }
    }

    private ErrorCode finishReasonError(String finishReason) {
        if (finishReason == null || finishReason.isBlank()) {
            return ErrorCode.AI_PROVIDER_INVALID_RESPONSE;
        }
        return switch (finishReason.toLowerCase(Locale.ROOT)) {
            case "stop" -> null;
            case "length", "max_tokens" -> ErrorCode.AI_PROVIDER_MAX_TOKENS;
            case "content_filter", "refusal" -> ErrorCode.AI_PROVIDER_REFUSAL;
            default -> ErrorCode.AI_PROVIDER_INVALID_RESPONSE;
        };
    }

    private ErrorCode errorCodeFor(StructuredConversionFailure failure) {
        return switch (failure) {
            case JSON_PARSE -> ErrorCode.AI_STRUCTURED_OUTPUT_JSON_PARSE_FAILED;
            case SCHEMA_VALIDATION -> ErrorCode.AI_STRUCTURED_OUTPUT_SCHEMA_VALIDATION_FAILED;
            case TYPE_CONVERSION -> ErrorCode.AI_STRUCTURED_OUTPUT_CONVERSION_FAILED;
        };
    }

    private AiProviderException structuredException(
            ErrorCode errorCode,
            AiUsageMetadata usage,
            int attempts
    ) {
        return new AiProviderException(
                errorCode,
                new IllegalStateException(errorCode.name()),
                usage,
                attempts
        );
    }

    private AiUsageMetadata mergeUsage(AiUsageMetadata accumulated, AiUsageMetadata current) {
        if (accumulated == null) {
            return current;
        }
        if (current == null) {
            return accumulated;
        }
        return new AiUsageMetadata(
                current.providerId() != null ? current.providerId() : accumulated.providerId(),
                current.providerRequestId() != null ? current.providerRequestId() : accumulated.providerRequestId(),
                current.modelId() != null ? current.modelId() : accumulated.modelId(),
                addNullable(accumulated.inputTokens(), current.inputTokens()),
                addNullable(accumulated.outputTokens(), current.outputTokens()),
                addNullable(accumulated.cacheReadInputTokens(), current.cacheReadInputTokens()),
                addNullable(accumulated.cacheWriteInputTokens(), current.cacheWriteInputTokens()),
                addNullable(accumulated.reasoningTokens(), current.reasoningTokens())
        );
    }

    private Long addNullable(Long left, Long right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left + right;
    }

    private AiProviderResponse generateOnce(AiProviderRequest request, AiProperties.ModelOptions options) {
        tokenBudgetEstimator.requireWithinBudget(
                request.feature(),
                new AiTokenBudgetInput(request.messages().stream()
                        .map(AiChatMessage::content)
                        .toList())
        );
        Future<ChatResponse> future = providerRequestExecutor.submit(() -> callProvider(request, options));

        try {
            ChatResponse response = future.get(options.timeout().toMillis(), TimeUnit.MILLISECONDS);
            return toResponse(response, options.model());
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw exceptionTranslator.translate(exception);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw exceptionTranslator.translate(exception);
        } catch (ExecutionException exception) {
            throw exceptionTranslator.translate(exception.getCause());
        }
    }

    private void sleepBeforeRetry(
            AiProperties.ModelOptions options,
            int attempts,
            AiProviderException lastException
    ) {
        long backoffMillis;
        if (lastException.getRetryAfter() != null) {
            backoffMillis = lastException.getRetryAfter().toMillis();
        } else {
            long multiplier = 1L << Math.min(attempts - 1, 30);
            try {
                backoffMillis = Math.multiplyExact(options.initialBackoff().toMillis(), multiplier);
            } catch (ArithmeticException ignored) {
                backoffMillis = Long.MAX_VALUE;
            }
        }
        long cappedMillis = Math.min(backoffMillis, options.maxBackoff().toMillis());

        try {
            retrySleeper.sleep(Duration.ofMillis(cappedMillis));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiProviderException(
                    ErrorCode.AI_PROVIDER_ERROR,
                    exception,
                    lastException.getUsageMetadata(),
                    attempts
            );
        }
    }

    private boolean isRetryable(ErrorCode errorCode) {
        return errorCode == ErrorCode.AI_RATE_LIMITED
                || errorCode == ErrorCode.AI_PROVIDER_OVERLOADED
                || errorCode == ErrorCode.AI_PROVIDER_UNAVAILABLE
                || errorCode == ErrorCode.AI_PROVIDER_TIMEOUT;
    }

    private ChatResponse callProvider(AiProviderRequest request, AiProperties.ModelOptions options) {
        ChatResponse response = chatClient.prompt()
                .messages(toSpringMessages(request.messages()))
                .options(providerOptions(options, reasoning(request.executionPolicy())))
                .call()
                .chatResponse();

        if (response == null) {
            throw new AiProviderException(
                    ErrorCode.AI_PROVIDER_INVALID_RESPONSE,
                    new IllegalStateException("AI Provider response is null")
            );
        }
        return response;
    }

    private List<Message> toSpringMessages(List<AiChatMessage> messages) {
        return messages.stream()
                .map(message -> switch (message.role()) {
                    case SYSTEM -> new SystemMessage(message.content());
                    case USER -> new UserMessage(message.content());
                    case ASSISTANT -> new AssistantMessage(message.content());
                })
                .map(Message.class::cast)
                .toList();
    }

    private OpenAiChatOptions.Builder providerOptions(AiProperties.ModelOptions options, String reasoningEffort) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(options.model())
                .n(1)
                .reasoningEffort(reasoningEffort)
                .timeout(options.timeout())
                .customHeaders(Map.of("Accept", "application/json"))
                .maxRetries(0);
        if (runtimeOptions.maxCompletionTokens()) {
            builder.maxCompletionTokens(options.maxOutputTokens());
        } else {
            builder.maxTokens(options.maxOutputTokens());
        }
        if (runtimeOptions.temperature() != null) {
            builder.temperature(runtimeOptions.temperature());
        }
        return builder;
    }

    private OpenAiChatOptions.Builder structuredProviderOptions(
            AiProperties.ModelOptions options,
            StructuredAiProviderRequest<?> request,
            String reasoningEffort
    ) {
        OpenAiChatModel.ResponseFormat.Builder responseFormat = OpenAiChatModel.ResponseFormat.builder();
        if (runtimeOptions.nativeStrictSchema()) {
            responseFormat.jsonSchema(request.outputDefinition().jsonSchema());
        } else {
            responseFormat.type(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT);
        }
        OpenAiChatOptions.Builder builder = providerOptions(options, reasoningEffort)
                .responseFormat(responseFormat.build());
        if (runtimeOptions.promptCachePolicy() != null) {
            OpenAiPromptCacheDecision cache = runtimeOptions.promptCachePolicy()
                    .decide(request, options.model());
            if (cache.enabled()) {
                builder.promptCacheKey(cache.cacheKey());
            }
            if (cache.explicit()) {
                builder.extraBody(Map.of(
                        "prompt_cache_options",
                        Map.of("mode", "explicit", "ttl", cache.ttl())
                ));
            }
        }
        return builder;
    }

    private AiProperties.ModelOptions options(
            com.wevo.backend.ai.domain.AiFeature feature,
            AiProviderExecutionPolicy policy
    ) {
        AiProperties.ModelOptions configured = properties.optionsFor(feature);
        return policy == null
                ? configured
                : configured.withExecutionSnapshot(policy.modelId(), policy.maxOutputTokens());
    }

    private String reasoning(AiProviderExecutionPolicy policy) {
        return policy == null ? runtimeOptions.reasoningEffort() : policy.reasoningEffort();
    }

    private AiProviderResponse toResponse(ChatResponse response, String requestedModel) {
        Generation generation = response.getResult();
        AiUsageMetadata usageMetadata = usageExtractor.extract(
                response.getMetadata(), requestedModel, runtimeOptions.providerId()
        );
        if (generation == null || generation.getOutput() == null) {
            throw new AiProviderException(
                    ErrorCode.AI_PROVIDER_INVALID_RESPONSE,
                    new IllegalStateException("AI Provider response result is empty"),
                    usageMetadata,
                    0
            );
        }
        if (hasProviderRefusal(generation)) {
            throw new AiProviderException(
                    ErrorCode.AI_PROVIDER_REFUSAL,
                    new IllegalStateException("AI Provider refused the request"),
                    usageMetadata,
                    0
            );
        }
        if (generation.getOutput().getText() == null || generation.getOutput().getText().isBlank()) {
            throw new AiProviderException(
                    ErrorCode.AI_PROVIDER_INVALID_RESPONSE,
                    new IllegalStateException("AI Provider response content is empty"),
                    usageMetadata,
                    0
            );
        }

        ChatGenerationMetadata generationMetadata = generation.getMetadata();
        String finishReason = generationMetadata == null ? null : generationMetadata.getFinishReason();
        ErrorCode finishError = finishReasonError(finishReason);
        if (finishError != null) {
            throw new AiProviderException(
                    finishError,
                    new IllegalStateException("AI Provider response finish reason is not successful"),
                    usageMetadata,
                    0
            );
        }

        return new AiProviderResponse(
                generation.getOutput().getText(),
                usageMetadata,
                finishReason,
                0
        );
    }

    private boolean hasProviderRefusal(Generation generation) {
        Object refusal = generation.getOutput().getMetadata().get("refusal");
        return refusal instanceof String text && !text.isBlank();
    }

    private final class UsageAccumulator {

        private AiUsageMetadata usage;

        private void add(AiUsageMetadata current) {
            usage = mergeUsage(usage, current);
        }

        private AiUsageMetadata snapshot() {
            return usage;
        }
    }

    private static final class AttemptCounter {

        private int value;

        private void increment() {
            value++;
        }

        private int value() {
            return value;
        }
    }

    private record ProviderStructuredResponse<T>(
            StructuredConversionResult<T> conversion,
            AiUsageMetadata usageMetadata,
            String finishReason,
            boolean contentEmpty
    ) {
    }
}
