package com.wevo.backend.ai.client;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.exception.ClaudeExceptionTranslator;
import com.wevo.backend.ai.exception.ClaudeProviderException;
import com.wevo.backend.global.exception.ErrorCode;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class SpringAiClaudeGateway implements ClaudeGateway {

    private final ChatClient chatClient;
    private final AiProperties properties;
    private final ClaudeExceptionTranslator exceptionTranslator;
    private final ExecutorService claudeRequestExecutor;
    private final AiUsageExtractor usageExtractor;
    private final AiRetrySleeper retrySleeper;

    public SpringAiClaudeGateway(
            ChatClient chatClient,
            AiProperties properties,
            ClaudeExceptionTranslator exceptionTranslator,
            ExecutorService claudeRequestExecutor,
            AiUsageExtractor usageExtractor,
            AiRetrySleeper retrySleeper
    ) {
        this.chatClient = chatClient;
        this.properties = properties;
        this.exceptionTranslator = exceptionTranslator;
        this.claudeRequestExecutor = claudeRequestExecutor;
        this.usageExtractor = usageExtractor;
        this.retrySleeper = retrySleeper;
    }

    @Override
    public ClaudeResponse generate(ClaudeRequest request) {
        AiProperties.ModelOptions options = properties.optionsFor(request.feature());
        int attempts = 0;

        while (true) {
            attempts++;
            try {
                ClaudeResponse response = generateOnce(request, options);
                return new ClaudeResponse(
                        response.content(),
                        response.usageMetadata(),
                        response.finishReason(),
                        attempts
                );
            } catch (ClaudeProviderException exception) {
                ClaudeProviderException contextual = exception.withAttemptContext(attempts);
                if (attempts > options.maxRetries() || !isRetryable(contextual.getErrorCode())) {
                    throw contextual;
                }
                sleepBeforeRetry(options, attempts, contextual);
            }
        }
    }

    @Override
    public <T> StructuredClaudeResponse<T> generateStructured(StructuredClaudeRequest<T> request) {
        AiProperties.ModelOptions options = properties.optionsFor(request.feature());
        UsageAccumulator usageAccumulator = new UsageAccumulator();
        AttemptCounter attemptCounter = new AttemptCounter();
        String userPrompt = request.prompt().userPrompt();
        int maxCorrectionRetries = properties.structuredOutput().maxCorrectionRetries();

        for (int correctionAttempt = 0; correctionAttempt <= maxCorrectionRetries; correctionAttempt++) {
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
                return new StructuredClaudeResponse<>(
                        conversion.value(),
                        request.prompt().id(),
                        request.outputDefinition().schemaId(),
                        usageAccumulator.snapshot(),
                        providerResponse.finishReason(),
                        attemptCounter.value()
                );
            }

            if (correctionAttempt == maxCorrectionRetries) {
                throw structuredException(
                        errorCodeFor(conversion.failure()),
                        usageAccumulator.snapshot(),
                        attemptCounter.value()
                );
            }
            userPrompt = correctionPrompt(request.prompt().userPrompt(), conversion.failure());
        }

        throw structuredException(
                ErrorCode.AI_STRUCTURED_OUTPUT_SCHEMA_VALIDATION_FAILED,
                usageAccumulator.snapshot(),
                attemptCounter.value()
        );
    }

    private <T> ProviderStructuredResponse<T> invokeStructuredWithProviderRetry(
            StructuredClaudeRequest<T> request,
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
            } catch (ClaudeProviderException exception) {
                if (transportAttempts > options.maxRetries() || !isRetryable(exception.getErrorCode())) {
                    AiUsageMetadata usage = mergeUsage(usageAccumulator.snapshot(), exception.getUsageMetadata());
                    throw new ClaudeProviderException(
                            exception.getErrorCode(),
                            exception.getCause(),
                            usage,
                            attemptCounter.value()
                    );
                }
                try {
                    sleepBeforeRetry(options, transportAttempts, exception);
                } catch (ClaudeProviderException interrupted) {
                    throw new ClaudeProviderException(
                            interrupted.getErrorCode(),
                            interrupted.getCause(),
                            mergeUsage(usageAccumulator.snapshot(), interrupted.getUsageMetadata()),
                            attemptCounter.value()
                    );
                }
            }
        }
    }

    private <T> ProviderStructuredResponse<T> generateStructuredOnce(
            StructuredClaudeRequest<T> request,
            String userPrompt,
            AiProperties.ModelOptions options
    ) {
        Future<ResponseEntity<ChatResponse, StructuredConversionResult<T>>> future = claudeRequestExecutor.submit(
                () -> callClaudeStructured(request, userPrompt, options)
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

    private <T> ResponseEntity<ChatResponse, StructuredConversionResult<T>> callClaudeStructured(
            StructuredClaudeRequest<T> request,
            String userPrompt,
            AiProperties.ModelOptions options
    ) {
        StrictStructuredOutputConverter<T> converter = new StrictStructuredOutputConverter<>(
                request.outputDefinition()
        );
        return chatClient.prompt()
                .system(request.prompt().systemPrompt())
                .user(userPrompt)
                .options(AnthropicChatOptions.builder()
                        .model(options.model())
                        .maxTokens(options.maxOutputTokens()))
                .call()
                .responseEntity(converter, spec -> spec.useProviderStructuredOutput());
    }

    private <T> ProviderStructuredResponse<T> toStructuredResponse(
            ResponseEntity<ChatResponse, StructuredConversionResult<T>> responseEntity,
            String requestedModel
    ) {
        if (responseEntity == null || responseEntity.response() == null) {
            throw new ClaudeProviderException(
                    ErrorCode.AI_PROVIDER_INVALID_RESPONSE,
                    new IllegalStateException("Claude structured response is null")
            );
        }

        ChatResponse response = responseEntity.response();
        AiUsageMetadata usage = usageExtractor.extract(response.getMetadata(), requestedModel);
        Generation generation = response.getResult();
        if (generation == null || generation.getOutput() == null) {
            throw new ClaudeProviderException(
                    ErrorCode.AI_PROVIDER_INVALID_RESPONSE,
                    new IllegalStateException("Claude structured result is empty"),
                    usage,
                    0
            );
        }
        String finishReason = generation.getMetadata() == null
                ? null
                : generation.getMetadata().getFinishReason();
        boolean contentEmpty = generation.getOutput().getText() == null
                || generation.getOutput().getText().isBlank();
        return new ProviderStructuredResponse<>(responseEntity.entity(), usage, finishReason, contentEmpty);
    }

    private <T> void validateSemantics(
            StructuredClaudeRequest<T> request,
            T value,
            AiUsageMetadata usage,
            int attempts
    ) {
        try {
            request.outputDefinition().validator().validate(value, request.validationContext());
        } catch (StructuredOutputSemanticException exception) {
            throw new ClaudeProviderException(
                    ErrorCode.AI_STRUCTURED_OUTPUT_SEMANTIC_VALIDATION_FAILED,
                    exception,
                    usage,
                    attempts
            );
        }
    }

    private ErrorCode finishReasonError(String finishReason) {
        if (finishReason == null) {
            return null;
        }
        return switch (finishReason.toLowerCase(Locale.ROOT)) {
            case "refusal" -> ErrorCode.AI_PROVIDER_REFUSAL;
            case "max_tokens" -> ErrorCode.AI_PROVIDER_MAX_TOKENS;
            default -> null;
        };
    }

    private ErrorCode errorCodeFor(StructuredConversionFailure failure) {
        return switch (failure) {
            case JSON_PARSE -> ErrorCode.AI_STRUCTURED_OUTPUT_JSON_PARSE_FAILED;
            case SCHEMA_VALIDATION -> ErrorCode.AI_STRUCTURED_OUTPUT_SCHEMA_VALIDATION_FAILED;
            case TYPE_CONVERSION -> ErrorCode.AI_STRUCTURED_OUTPUT_CONVERSION_FAILED;
        };
    }

    private String correctionPrompt(String originalUserPrompt, StructuredConversionFailure failure) {
        String reason = switch (failure) {
            case JSON_PARSE -> "The previous output was not valid JSON.";
            case SCHEMA_VALIDATION -> "The previous output did not match the required JSON schema.";
            case TYPE_CONVERSION -> "The previous output could not be converted to the required result type.";
        };
        return originalUserPrompt + "\n\n<output_correction>\n"
                + reason
                + " Return only one complete JSON object that matches the provided schema."
                + "\n</output_correction>";
    }

    private ClaudeProviderException structuredException(
            ErrorCode errorCode,
            AiUsageMetadata usage,
            int attempts
    ) {
        return new ClaudeProviderException(
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
                current.providerRequestId() != null ? current.providerRequestId() : accumulated.providerRequestId(),
                current.modelId() != null ? current.modelId() : accumulated.modelId(),
                addNullable(accumulated.inputTokens(), current.inputTokens()),
                addNullable(accumulated.outputTokens(), current.outputTokens()),
                addNullable(accumulated.cacheReadInputTokens(), current.cacheReadInputTokens()),
                addNullable(accumulated.cacheWriteInputTokens(), current.cacheWriteInputTokens())
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

    private ClaudeResponse generateOnce(ClaudeRequest request, AiProperties.ModelOptions options) {
        Future<ChatResponse> future = claudeRequestExecutor.submit(() -> callClaude(request, options));

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
            ClaudeProviderException lastException
    ) {
        long multiplier = 1L << Math.min(attempts - 1, 30);
        long backoffMillis;
        try {
            backoffMillis = Math.multiplyExact(options.initialBackoff().toMillis(), multiplier);
        } catch (ArithmeticException ignored) {
            backoffMillis = Long.MAX_VALUE;
        }
        long cappedMillis = Math.min(backoffMillis, options.maxBackoff().toMillis());

        try {
            retrySleeper.sleep(java.time.Duration.ofMillis(cappedMillis));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ClaudeProviderException(
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
                || errorCode == ErrorCode.AI_PROVIDER_UNAVAILABLE;
    }

    private ChatResponse callClaude(ClaudeRequest request, AiProperties.ModelOptions options) {
        ChatResponse response = chatClient.prompt()
                .system(request.systemPrompt())
                .user(request.userPrompt())
                .options(AnthropicChatOptions.builder()
                        .model(options.model())
                        .maxTokens(options.maxOutputTokens()))
                .call()
                .chatResponse();

        if (response == null) {
            throw new ClaudeProviderException(
                    ErrorCode.AI_PROVIDER_INVALID_RESPONSE,
                    new IllegalStateException("Claude response is null")
            );
        }
        return response;
    }

    private ClaudeResponse toResponse(ChatResponse response, String requestedModel) {
        Generation generation = response.getResult();
        ChatResponseMetadata metadata = response.getMetadata();
        AiUsageMetadata usageMetadata = usageExtractor.extract(metadata, requestedModel);
        if (generation == null
                || generation.getOutput() == null
                || generation.getOutput().getText() == null
                || generation.getOutput().getText().isBlank()) {
            throw new ClaudeProviderException(
                    ErrorCode.AI_PROVIDER_INVALID_RESPONSE,
                    new IllegalStateException("Claude response content is empty"),
                    usageMetadata,
                    0
            );
        }

        ChatGenerationMetadata generationMetadata = generation.getMetadata();

        return new ClaudeResponse(
                generation.getOutput().getText(),
                usageMetadata,
                generationMetadata.getFinishReason(),
                0
        );
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
