package com.wevo.backend.ai.client;

import com.anthropic.core.JsonValue;
import com.anthropic.core.http.Headers;
import com.anthropic.errors.AnthropicServiceException;
import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.exception.ClaudeExceptionTranslator;
import com.wevo.backend.ai.exception.ClaudeProviderException;
import com.wevo.backend.ai.prompt.PromptTemplateId;
import com.wevo.backend.ai.prompt.RenderedPrompt;
import com.wevo.backend.global.exception.ErrorCode;
import org.springframework.ai.anthropic.AnthropicChatOptions;
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
import org.springframework.ai.model.tool.StructuredOutputChatOptions;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
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
                new ClaudeRequest(AiFeature.DRAFT_REVIEW, "You are a test assistant.", "Reply with pong.")
        );

        assertThat(response.content()).isEqualTo("pong");
        assertThat(response.usageMetadata().providerRequestId()).isEqualTo("request-1");
        assertThat(response.usageMetadata().modelId()).isEqualTo("response-model");
        assertThat(response.usageMetadata().inputTokens()).isEqualTo(5L);
        assertThat(response.usageMetadata().outputTokens()).isEqualTo(2L);
        assertThat(response.usageMetadata().totalInputTokens()).isEqualTo(5L);
        assertThat(response.finishReason()).isEqualTo("end_turn");
        assertThat(response.attemptCount()).isEqualTo(1);

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
                new ClaudeRequest(AiFeature.DRAFT_REVIEW, "system", "user")
        ))
                .isInstanceOf(ClaudeProviderException.class)
                .extracting(exception -> ((ClaudeProviderException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_PROVIDER_TIMEOUT);
    }

    @Test
    void translatesProviderRateLimitError() {
        AtomicInteger attempts = new AtomicInteger();
        ChatModel failingModel = prompt -> {
            attempts.incrementAndGet();
            throw new TestServiceException(429);
        };
        SpringAiClaudeGateway gateway = gateway(failingModel, Duration.ofSeconds(1));

        assertThatThrownBy(() -> gateway.generate(
                new ClaudeRequest(AiFeature.DRAFT_REVIEW, "system", "user")
        ))
                .isInstanceOf(ClaudeProviderException.class)
                .extracting(exception -> ((ClaudeProviderException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_RATE_LIMITED);
        assertThat(attempts).hasValue(3);
    }

    @Test
    void retriesRateLimitAndReturnsTotalAttemptCountOnSuccess() {
        AtomicInteger attempts = new AtomicInteger();
        ChatModel recoveringModel = prompt -> {
            if (attempts.incrementAndGet() == 1) {
                throw new TestServiceException(429);
            }
            return response("recovered");
        };
        SpringAiClaudeGateway gateway = gateway(recoveringModel, Duration.ofSeconds(1));

        ClaudeResponse response = gateway.generate(
                new ClaudeRequest(AiFeature.DRAFT_REVIEW, "system", "user")
        );

        assertThat(response.content()).isEqualTo("recovered");
        assertThat(response.attemptCount()).isEqualTo(2);
        assertThat(attempts).hasValue(2);
    }

    @Test
    void doesNotRetryNonRetryableProviderError() {
        AtomicInteger attempts = new AtomicInteger();
        ChatModel failingModel = prompt -> {
            attempts.incrementAndGet();
            throw new TestServiceException(400);
        };
        SpringAiClaudeGateway gateway = gateway(failingModel, Duration.ofSeconds(1));

        assertThatThrownBy(() -> gateway.generate(
                new ClaudeRequest(AiFeature.DRAFT_REVIEW, "system", "user")
        ))
                .isInstanceOf(ClaudeProviderException.class)
                .satisfies(exception -> assertThat(((ClaudeProviderException) exception).getAttemptCount())
                        .isEqualTo(1));
        assertThat(attempts).hasValue(1);
    }

    @Test
    void rejectsEmptyProviderResponse() {
        ChatModel emptyModel = prompt -> response(" ");
        SpringAiClaudeGateway gateway = gateway(emptyModel, Duration.ofSeconds(1));

        assertThatThrownBy(() -> gateway.generate(
                new ClaudeRequest(AiFeature.DRAFT_REVIEW, "system", "user")
        ))
                .isInstanceOf(ClaudeProviderException.class)
                .extracting(exception -> ((ClaudeProviderException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_PROVIDER_INVALID_RESPONSE);
    }

    @Test
    void returnsTypedStructuredOutputWithNativeSchemaAndMetadata() {
        AtomicReference<Prompt> capturedPrompt = new AtomicReference<>();
        ChatModel chatModel = prompt -> {
            capturedPrompt.set(prompt);
            return response("{\"resourceId\":7,\"signal\":\"CLEAR\"}");
        };
        SpringAiClaudeGateway gateway = structuredGateway(chatModel, 2);

        StructuredClaudeResponse<TestOutput> response = gateway.generateStructured(structuredRequest(Set.of(7L)));

        assertThat(response.result()).isEqualTo(new TestOutput(7L, TestSignal.CLEAR));
        assertThat(response.promptId().trackingValue()).isEqualTo("contract-summary:v1");
        assertThat(response.schemaId().trackingValue()).isEqualTo("test-output:v1");
        assertThat(response.usageMetadata().providerRequestId()).isEqualTo("request-1");
        assertThat(response.attemptCount()).isEqualTo(1);
        assertThat(capturedPrompt.get().getInstructions())
                .extracting(message -> message.getText().strip())
                .containsExactly("system", "user");
        assertThat(capturedPrompt.get().getOptions()).isInstanceOf(StructuredOutputChatOptions.class);
        assertThat(((StructuredOutputChatOptions) capturedPrompt.get().getOptions()).getOutputSchema())
                .contains("resourceId", "signal");
    }

    @Test
    void retriesInvalidSchemaWithoutCopyingInvalidOutputAndAggregatesUsage() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Prompt> correctedPrompt = new AtomicReference<>();
        ChatModel chatModel = prompt -> {
            if (calls.incrementAndGet() == 1) {
                return response("invalid-secret-output");
            }
            correctedPrompt.set(prompt);
            return response("{\"resourceId\":7,\"signal\":\"CLEAR\"}");
        };
        SpringAiClaudeGateway gateway = structuredGateway(chatModel, 2);

        StructuredClaudeResponse<TestOutput> response = gateway.generateStructured(structuredRequest(Set.of(7L)));

        assertThat(calls).hasValue(2);
        assertThat(response.attemptCount()).isEqualTo(2);
        assertThat(response.usageMetadata().inputTokens()).isEqualTo(10L);
        assertThat(response.usageMetadata().outputTokens()).isEqualTo(4L);
        assertThat(correctedPrompt.get().getInstructions().get(1).getText())
                .contains("<output_correction>")
                .doesNotContain("invalid-secret-output");
    }

    @Test
    void stopsCorrectionRetriesAtConfiguredLimitAndClassifiesFinalFailure() {
        AtomicInteger calls = new AtomicInteger();
        ChatModel chatModel = prompt -> {
            calls.incrementAndGet();
            return response("not-json");
        };
        SpringAiClaudeGateway gateway = structuredGateway(chatModel, 2);

        assertThatThrownBy(() -> gateway.generateStructured(structuredRequest(Set.of(7L))))
                .isInstanceOf(ClaudeProviderException.class)
                .satisfies(exception -> {
                    ClaudeProviderException providerException = (ClaudeProviderException) exception;
                    assertThat(providerException.getErrorCode())
                            .isEqualTo(ErrorCode.AI_STRUCTURED_OUTPUT_JSON_PARSE_FAILED);
                    assertThat(providerException.getAttemptCount()).isEqualTo(3);
                    assertThat(providerException.getUsageMetadata().inputTokens()).isEqualTo(15L);
                    assertThat(providerException.getUsageMetadata().outputTokens()).isEqualTo(6L);
                });
        assertThat(calls).hasValue(3);
    }

    @Test
    void rejectsWrongEnumAfterSchemaCorrectionLimit() {
        ChatModel chatModel = prompt -> response("{\"resourceId\":7,\"signal\":\"clear\"}");
        SpringAiClaudeGateway gateway = structuredGateway(chatModel, 0);

        assertThatThrownBy(() -> gateway.generateStructured(structuredRequest(Set.of(7L))))
                .isInstanceOf(ClaudeProviderException.class)
                .extracting(exception -> ((ClaudeProviderException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_STRUCTURED_OUTPUT_SCHEMA_VALIDATION_FAILED);
    }

    @Test
    void rejectsResourceOutsideSemanticAllowlistWithoutRetry() {
        AtomicInteger calls = new AtomicInteger();
        ChatModel chatModel = prompt -> {
            calls.incrementAndGet();
            return response("{\"resourceId\":7,\"signal\":\"CLEAR\"}");
        };
        SpringAiClaudeGateway gateway = structuredGateway(chatModel, 2);

        assertThatThrownBy(() -> gateway.generateStructured(structuredRequest(Set.of(8L))))
                .isInstanceOf(ClaudeProviderException.class)
                .extracting(exception -> ((ClaudeProviderException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_STRUCTURED_OUTPUT_SEMANTIC_VALIDATION_FAILED);
        assertThat(calls).hasValue(1);
    }

    @Test
    void classifiesRefusalAndMaxTokensBeforeParsing() {
        ConcurrentLinkedQueue<ChatResponse> responses = new ConcurrentLinkedQueue<>();
        responses.add(response("refused", "refusal"));
        responses.add(response(" ", "max_tokens"));
        SpringAiClaudeGateway gateway = structuredGateway(prompt -> responses.remove(), 2);

        assertThatThrownBy(() -> gateway.generateStructured(structuredRequest(Set.of(7L))))
                .isInstanceOf(ClaudeProviderException.class)
                .extracting(exception -> ((ClaudeProviderException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_PROVIDER_REFUSAL);
        assertThatThrownBy(() -> gateway.generateStructured(structuredRequest(Set.of(7L))))
                .isInstanceOf(ClaudeProviderException.class)
                .extracting(exception -> ((ClaudeProviderException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_PROVIDER_MAX_TOKENS);
    }

    @Test
    void rejectsEmptyStructuredResponse() {
        SpringAiClaudeGateway gateway = structuredGateway(prompt -> response(" "), 2);

        assertThatThrownBy(() -> gateway.generateStructured(structuredRequest(Set.of(7L))))
                .isInstanceOf(ClaudeProviderException.class)
                .extracting(exception -> ((ClaudeProviderException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_PROVIDER_INVALID_RESPONSE);
    }

    private SpringAiClaudeGateway gateway(ChatModel chatModel, Duration timeout) {
        AiProperties properties = new AiProperties(
                new AiProperties.ModelOptions(
                        "test-model", timeout, 128, 2, Duration.ZERO, Duration.ZERO
                ),
                Map.of(),
                null
        );
        return new SpringAiClaudeGateway(
                ChatClient.builder(chatModel)
                        .defaultOptions(AnthropicChatOptions.builder()
                                .model("test-model")
                                .maxTokens(128))
                        .build(),
                properties,
                new ClaudeExceptionTranslator(),
                executor,
                new AiUsageExtractor(),
                new AiRetrySleeper() {
                    @Override
                    public void sleep(Duration duration) {
                        // 테스트에서는 backoff를 기다리지 않는다.
                    }
                }
        );
    }

    private SpringAiClaudeGateway structuredGateway(ChatModel chatModel, int maxCorrectionRetries) {
        AiProperties properties = new AiProperties(
                new AiProperties.ModelOptions(
                        "test-model", Duration.ofSeconds(1), 128, 0, Duration.ZERO, Duration.ZERO
                ),
                Map.of(),
                new AiProperties.StructuredOutputOptions(maxCorrectionRetries)
        );
        ChatModel structuredOutputModel = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return chatModel.call(prompt);
            }

            @Override
            public ChatOptions getOptions() {
                return AnthropicChatOptions.builder()
                        .model("test-model")
                        .maxTokens(128)
                        .build();
            }
        };
        return new SpringAiClaudeGateway(
                ChatClient.builder(structuredOutputModel).build(),
                properties,
                new ClaudeExceptionTranslator(),
                executor,
                new AiUsageExtractor(),
                new AiRetrySleeper() {
                    @Override
                    public void sleep(Duration duration) {
                    }
                }
        );
    }

    private StructuredClaudeRequest<TestOutput> structuredRequest(Set<Long> allowedResourceIds) {
        StructuredOutputDefinition<TestOutput> definition = StructuredOutputDefinition.of(
                new OutputSchemaId("test-output", 1),
                TestOutput.class,
                (output, context) -> context.requireAllowedResourceId(output.resourceId())
        );
        return new StructuredClaudeRequest<>(
                AiFeature.DRAFT_REVIEW,
                new RenderedPrompt(new PromptTemplateId("contract-summary", 1), "system", "user"),
                definition,
                new StructuredOutputValidationContext(allowedResourceIds)
        );
    }

    private ChatResponse response(String content) {
        return response(content, "end_turn");
    }

    private ChatResponse response(String content, String finishReason) {
        ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder()
                .finishReason(finishReason)
                .build();
        Generation generation = new Generation(new AssistantMessage(content), generationMetadata);
        ChatResponseMetadata responseMetadata = ChatResponseMetadata.builder()
                .id("request-1")
                .model("response-model")
                .usage(new DefaultUsage(5, 2, 7))
                .build();
        return new ChatResponse(List.of(generation), responseMetadata);
    }

    private record TestOutput(Long resourceId, TestSignal signal) {
    }

    private enum TestSignal {
        CLEAR,
        PARTIAL,
        UNCLEAR
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
