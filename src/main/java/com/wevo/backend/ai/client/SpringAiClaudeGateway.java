package com.wevo.backend.ai.client;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.exception.ClaudeExceptionTranslator;
import com.wevo.backend.ai.exception.ClaudeProviderException;
import com.wevo.backend.global.exception.ErrorCode;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Component;

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
}
