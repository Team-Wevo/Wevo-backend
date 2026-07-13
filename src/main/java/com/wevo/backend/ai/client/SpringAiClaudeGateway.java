package com.wevo.backend.ai.client;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.exception.ClaudeExceptionTranslator;
import com.wevo.backend.ai.exception.ClaudeProviderException;
import com.wevo.backend.global.exception.ErrorCode;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
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

    public SpringAiClaudeGateway(
            ChatClient chatClient,
            AiProperties properties,
            ClaudeExceptionTranslator exceptionTranslator,
            ExecutorService claudeRequestExecutor
    ) {
        this.chatClient = chatClient;
        this.properties = properties;
        this.exceptionTranslator = exceptionTranslator;
        this.claudeRequestExecutor = claudeRequestExecutor;
    }

    @Override
    public ClaudeResponse generate(ClaudeRequest request) {
        AiProperties.ModelOptions options = properties.optionsFor(request.featureName());
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
        if (generation == null
                || generation.getOutput() == null
                || generation.getOutput().getText() == null
                || generation.getOutput().getText().isBlank()) {
            throw new ClaudeProviderException(
                    ErrorCode.AI_PROVIDER_INVALID_RESPONSE,
                    new IllegalStateException("Claude response content is empty")
            );
        }

        ChatResponseMetadata metadata = response.getMetadata();
        Usage usage = metadata.getUsage();
        ChatGenerationMetadata generationMetadata = generation.getMetadata();

        return new ClaudeResponse(
                generation.getOutput().getText(),
                metadata.getId(),
                metadata.getModel() == null ? requestedModel : metadata.getModel(),
                usage.getPromptTokens(),
                usage.getCompletionTokens(),
                usage.getTotalTokens(),
                usage.getCacheReadInputTokens(),
                usage.getCacheWriteInputTokens(),
                generationMetadata.getFinishReason()
        );
    }
}
