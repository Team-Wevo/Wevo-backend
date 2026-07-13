package com.wevo.backend.ai.client;

import com.anthropic.core.JsonValue;
import com.anthropic.core.http.Headers;
import com.anthropic.errors.AnthropicServiceException;
import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.exception.ClaudeExceptionTranslator;
import com.wevo.backend.ai.exception.ClaudeProviderException;
import com.wevo.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringAiClaudeGatewayTest {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void tearDown() {
        executor.close();
    }

    @Test
    void returnsContentAndUsageMetadataWithoutNetworkCall() {
        AtomicReference<Prompt> capturedPrompt = new AtomicReference<>();
        ChatModel chatModel = prompt -> {
            capturedPrompt.set(prompt);
            return response("pong");
        };
        SpringAiClaudeGateway gateway = gateway(chatModel, Duration.ofSeconds(1));

        ClaudeResponse response = gateway.generate(
                new ClaudeRequest("smoke", "You are a test assistant.", "Reply with pong.")
        );

        assertThat(response.content()).isEqualTo("pong");
        assertThat(response.requestId()).isEqualTo("request-1");
        assertThat(response.model()).isEqualTo("response-model");
        assertThat(response.promptTokens()).isEqualTo(5);
        assertThat(response.completionTokens()).isEqualTo(2);
        assertThat(response.totalTokens()).isEqualTo(7);
        assertThat(response.finishReason()).isEqualTo("end_turn");

        ChatOptions options = capturedPrompt.get().getOptions();
        assertThat(options.getModel()).isEqualTo("test-model");
        assertThat(options.getMaxTokens()).isEqualTo(128);
        assertThat(capturedPrompt.get().getInstructions())
                .extracting(message -> message.getText())
                .containsExactly("You are a test assistant.", "Reply with pong.");
    }

    @Test
    void appliesFeatureDeadline() {
        ChatModel slowModel = prompt -> {
            try {
                Thread.sleep(Duration.ofSeconds(1));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return response("late response");
        };
        SpringAiClaudeGateway gateway = gateway(slowModel, Duration.ofMillis(30));

        assertThatThrownBy(() -> gateway.generate(
                new ClaudeRequest("smoke", "system", "user")
        ))
                .isInstanceOf(ClaudeProviderException.class)
                .extracting(exception -> ((ClaudeProviderException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_PROVIDER_TIMEOUT);
    }

    @Test
    void translatesProviderRateLimitError() {
        ChatModel failingModel = prompt -> {
            throw new TestServiceException(429);
        };
        SpringAiClaudeGateway gateway = gateway(failingModel, Duration.ofSeconds(1));

        assertThatThrownBy(() -> gateway.generate(
                new ClaudeRequest("smoke", "system", "user")
        ))
                .isInstanceOf(ClaudeProviderException.class)
                .extracting(exception -> ((ClaudeProviderException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_RATE_LIMITED);
    }

    @Test
    void rejectsEmptyProviderResponse() {
        ChatModel emptyModel = prompt -> response(" ");
        SpringAiClaudeGateway gateway = gateway(emptyModel, Duration.ofSeconds(1));

        assertThatThrownBy(() -> gateway.generate(
                new ClaudeRequest("smoke", "system", "user")
        ))
                .isInstanceOf(ClaudeProviderException.class)
                .extracting(exception -> ((ClaudeProviderException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_PROVIDER_INVALID_RESPONSE);
    }

    private SpringAiClaudeGateway gateway(ChatModel chatModel, Duration timeout) {
        AiProperties properties = new AiProperties(
                new AiProperties.ModelOptions("test-model", timeout, 128),
                Map.of()
        );
        return new SpringAiClaudeGateway(
                ChatClient.builder(chatModel).build(),
                properties,
                new ClaudeExceptionTranslator(),
                executor
        );
    }

    private ChatResponse response(String content) {
        ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder()
                .finishReason("end_turn")
                .build();
        Generation generation = new Generation(new AssistantMessage(content), generationMetadata);
        ChatResponseMetadata responseMetadata = ChatResponseMetadata.builder()
                .id("request-1")
                .model("response-model")
                .usage(new DefaultUsage(5, 2, 7))
                .build();
        return new ChatResponse(List.of(generation), responseMetadata);
    }

    private static final class TestServiceException extends AnthropicServiceException {

        private final int statusCode;

        private TestServiceException(int statusCode) {
            super("provider error", null);
            this.statusCode = statusCode;
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public Headers headers() {
            return null;
        }

        @Override
        public JsonValue body() {
            return null;
        }
    }
}
