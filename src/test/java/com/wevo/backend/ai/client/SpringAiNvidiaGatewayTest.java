package com.wevo.backend.ai.client;

import com.openai.core.JsonValue;
import com.openai.core.http.Headers;
import com.openai.errors.OpenAIServiceException;
import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.config.NvidiaProviderProperties;
import com.wevo.backend.ai.context.AiTokenBudgetEstimator;
import com.wevo.backend.ai.context.AiInputBudgetExceededException;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.exception.AiProviderException;
import com.wevo.backend.ai.exception.NvidiaExceptionTranslator;
import com.wevo.backend.ai.prompt.PromptTemplateId;
import com.wevo.backend.ai.prompt.RenderedPrompt;
import com.wevo.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringAiNvidiaGatewayTest {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void tearDown() {
        executor.close();
    }

    @Test
    void returnsContentUsageAndAllConversationMessagesWithoutNetworkCall() {
        AtomicReference<Prompt> capturedPrompt = new AtomicReference<>();
        ChatModel chatModel = prompt -> {
            capturedPrompt.set(prompt);
            return response("pong");
        };
        SpringAiNvidiaGateway gateway = gateway(chatModel, Duration.ofSeconds(1), 2, 2);

        AiProviderResponse response = gateway.generate(new AiProviderRequest(
                AiFeature.DRAFT_REVIEW,
                List.of(
                        new AiChatMessage(AiChatMessage.Role.SYSTEM, "You are a test assistant."),
                        new AiChatMessage(AiChatMessage.Role.USER, "First question"),
                        new AiChatMessage(AiChatMessage.Role.ASSISTANT, "First answer"),
                        new AiChatMessage(AiChatMessage.Role.USER, "Reply with pong.")
                )
        ));

        assertThat(response.content()).isEqualTo("pong");
        assertThat(response.usageMetadata().providerId()).isEqualTo("nvidia");
        assertThat(response.usageMetadata().providerRequestId()).isEqualTo("request-1");
        assertThat(response.usageMetadata().modelId()).isEqualTo("response-model");
        assertThat(response.usageMetadata().inputTokens()).isEqualTo(5L);
        assertThat(response.usageMetadata().outputTokens()).isEqualTo(2L);
        assertThat(response.usageMetadata().cacheReadInputTokens()).isNull();
        assertThat(response.usageMetadata().cacheWriteInputTokens()).isNull();
        assertThat(response.finishReason()).isEqualTo("stop");
        assertThat(response.attemptCount()).isEqualTo(1);

        ChatOptions options = capturedPrompt.get().getOptions();
        assertThat(options).isInstanceOf(OpenAiChatOptions.class);
        assertThat(options.getModel()).isEqualTo("test-model");
        assertThat(options.getMaxTokens()).isEqualTo(128);
        assertThat(((OpenAiChatOptions) options).getMaxRetries()).isZero();
        assertThat(((OpenAiChatOptions) options).getReasoningEffort()).isEqualTo("none");
        assertThat(((OpenAiChatOptions) options).getResponseFormat()).isNull();
        assertThat(capturedPrompt.get().getInstructions())
                .extracting(message -> message.getMessageType().getValue(), message -> message.getText())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("system", "You are a test assistant."),
                        org.assertj.core.groups.Tuple.tuple("user", "First question"),
                        org.assertj.core.groups.Tuple.tuple("assistant", "First answer"),
                        org.assertj.core.groups.Tuple.tuple("user", "Reply with pong.")
                );
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
        SpringAiNvidiaGateway gateway = gateway(slowModel, Duration.ofMillis(30), 0, 2);

        assertThatThrownBy(() -> gateway.generate(
                new AiProviderRequest(AiFeature.DRAFT_REVIEW, "system", "user")
        ))
                .isInstanceOf(AiProviderException.class)
                .extracting(exception -> ((AiProviderException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_PROVIDER_TIMEOUT);
    }

    @Test
    void rejectsFinalRenderedInputBeforeCallingProvider() {
        AtomicInteger providerCalls = new AtomicInteger();
        SpringAiNvidiaGateway gateway = gateway(prompt -> {
            providerCalls.incrementAndGet();
            return response("must not be called");
        }, Duration.ofSeconds(1), 0, 2);
        String oversized = "x".repeat(100_001);

        assertThatThrownBy(() -> gateway.generate(
                new AiProviderRequest(AiFeature.DRAFT_REVIEW, "system", oversized)
        ))
                .isInstanceOf(AiInputBudgetExceededException.class)
                .hasMessageNotContaining(oversized);
        assertThat(providerCalls).hasValue(0);
    }

    @Test
    void retriesOnlyRetryableTransportErrorsAndReturnsActualAttemptCount() {
        AtomicInteger attempts = new AtomicInteger();
        ChatModel recoveringModel = prompt -> {
            if (attempts.incrementAndGet() == 1) {
                throw new TestServiceException(429);
            }
            return response("recovered");
        };
        SpringAiNvidiaGateway gateway = gateway(recoveringModel, Duration.ofSeconds(1), 2, 2);

        AiProviderResponse response = gateway.generate(
                new AiProviderRequest(AiFeature.DRAFT_REVIEW, "system", "user")
        );

        assertThat(response.attemptCount()).isEqualTo(2);
        assertThat(attempts).hasValue(2);

        AtomicInteger invalidAttempts = new AtomicInteger();
        SpringAiNvidiaGateway invalidGateway = gateway(prompt -> {
            invalidAttempts.incrementAndGet();
            throw new TestServiceException(422);
        }, Duration.ofSeconds(1), 2, 2);

        assertThatThrownBy(() -> invalidGateway.generate(
                new AiProviderRequest(AiFeature.DRAFT_REVIEW, "system", "user")
        ))
                .isInstanceOf(AiProviderException.class)
                .satisfies(exception -> {
                    AiProviderException providerException = (AiProviderException) exception;
                    assertThat(providerException.getErrorCode()).isEqualTo(ErrorCode.AI_INVALID_REQUEST);
                    assertThat(providerException.getAttemptCount()).isEqualTo(1);
                });
        assertThat(invalidAttempts).hasValue(1);
    }

    @Test
    void retriesProviderTimeoutWithinConfiguredBound() {
        AtomicInteger attempts = new AtomicInteger();
        ChatModel recoveringModel = prompt -> {
            if (attempts.incrementAndGet() == 1) {
                throw new TestServiceException(408);
            }
            return response("recovered");
        };
        SpringAiNvidiaGateway gateway = gateway(recoveringModel, Duration.ofSeconds(1), 1, 0);

        AiProviderResponse response = gateway.generate(
                new AiProviderRequest(AiFeature.DRAFT_REVIEW, "system", "user")
        );

        assertThat(response.attemptCount()).isEqualTo(2);
        assertThat(attempts).hasValue(2);
    }

    @Test
    void prefersProviderRetryAfterWithinConfiguredBound() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicReference<Duration> slept = new AtomicReference<>();
        ChatModel model = prompt -> {
            if (attempts.incrementAndGet() == 1) {
                throw new TestServiceException(429, "2");
            }
            return response("recovered");
        };
        AiRetrySleeper sleeper = new AiRetrySleeper() {
            @Override
            public void sleep(Duration duration) {
                slept.set(duration);
            }
        };
        SpringAiNvidiaGateway gateway = gateway(
                model, Duration.ofSeconds(1), 1, 2, sleeper
        );

        gateway.generate(new AiProviderRequest(AiFeature.DRAFT_REVIEW, "system", "user"));

        assertThat(slept.get()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void rejectsEmptyUnknownFinishAndMaxTokenResponses() {
        ConcurrentLinkedQueue<ChatResponse> responses = new ConcurrentLinkedQueue<>();
        responses.add(response(" "));
        responses.add(response("tool call", "tool_calls"));
        responses.add(response("truncated", "length"));
        SpringAiNvidiaGateway gateway = gateway(prompt -> responses.remove(), Duration.ofSeconds(1), 0, 2);

        assertError(gateway, ErrorCode.AI_PROVIDER_INVALID_RESPONSE);
        assertError(gateway, ErrorCode.AI_PROVIDER_INVALID_RESPONSE);
        assertError(gateway, ErrorCode.AI_PROVIDER_MAX_TOKENS);
    }

    @Test
    void requestsProviderJsonModeAndStillAppliesServerSchemaValidation() {
        AtomicReference<Prompt> capturedPrompt = new AtomicReference<>();
        ChatModel chatModel = prompt -> {
            capturedPrompt.set(prompt);
            return response("{\"resourceId\":7,\"signal\":\"CLEAR\"}");
        };
        SpringAiNvidiaGateway gateway = gateway(chatModel, Duration.ofSeconds(1), 0, 2);

        StructuredAiProviderResponse<TestOutput> response = gateway.generateStructured(
                structuredRequest(Set.of(7L))
        );

        assertThat(response.result()).isEqualTo(new TestOutput(7L, TestSignal.CLEAR));
        assertThat(response.schemaId().trackingValue()).isEqualTo("test-output:v1");
        assertThat(response.attemptCount()).isEqualTo(1);
        assertThat(capturedPrompt.get().getInstructions().get(1).getText())
                .contains("<output_contract>", "resourceId", "signal");
        OpenAiChatOptions options = (OpenAiChatOptions) capturedPrompt.get().getOptions();
        assertThat(options.getReasoningEffort()).isEqualTo("none");
        assertThat(options.getResponseFormat()).isNotNull();
        assertThat(options.getResponseFormat().getType())
                .isEqualTo(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT);
    }

    @Test
    void retriesMalformedAndMissingStructuredValuesWithoutCopyingOutputAndAccumulatesUsage() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Prompt> correctedPrompt = new AtomicReference<>();
        ChatModel chatModel = prompt -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                return response("invalid-secret-output");
            }
            if (call == 2) {
                return response("{\"resourceId\":7}");
            }
            correctedPrompt.set(prompt);
            return response("{\"resourceId\":7,\"signal\":\"CLEAR\"}");
        };
        SpringAiNvidiaGateway gateway = gateway(chatModel, Duration.ofSeconds(1), 0, 2);

        StructuredAiProviderResponse<TestOutput> response = gateway.generateStructured(
                structuredRequest(Set.of(7L))
        );

        assertThat(calls).hasValue(3);
        assertThat(response.attemptCount()).isEqualTo(3);
        assertThat(response.usageMetadata().inputTokens()).isEqualTo(15L);
        assertThat(response.usageMetadata().outputTokens()).isEqualTo(6L);
        assertThat(correctedPrompt.get().getInstructions().get(1).getText())
                .contains("<output_correction>")
                .doesNotContain("invalid-secret-output");
    }

    @Test
    void classifiesFinalStructuredFailuresAndDoesNotRetrySemanticFailure() {
        SpringAiNvidiaGateway malformedGateway = gateway(
                prompt -> response("not-json"), Duration.ofSeconds(1), 0, 0
        );
        assertStructuredError(
                malformedGateway,
                structuredRequest(Set.of(7L)),
                ErrorCode.AI_STRUCTURED_OUTPUT_JSON_PARSE_FAILED
        );

        SpringAiNvidiaGateway schemaGateway = gateway(
                prompt -> response("{\"resourceId\":7}"), Duration.ofSeconds(1), 0, 0
        );
        assertStructuredError(
                schemaGateway,
                structuredRequest(Set.of(7L)),
                ErrorCode.AI_STRUCTURED_OUTPUT_SCHEMA_VALIDATION_FAILED
        );

        AtomicInteger semanticCalls = new AtomicInteger();
        SpringAiNvidiaGateway semanticGateway = gateway(prompt -> {
            semanticCalls.incrementAndGet();
            return response("{\"resourceId\":7,\"signal\":\"CLEAR\"}");
        }, Duration.ofSeconds(1), 0, 2);
        assertStructuredError(
                semanticGateway,
                structuredRequest(Set.of(8L)),
                ErrorCode.AI_STRUCTURED_OUTPUT_SEMANTIC_VALIDATION_FAILED
        );
        assertThat(semanticCalls).hasValue(1);
    }

    private void assertError(SpringAiNvidiaGateway gateway, ErrorCode errorCode) {
        assertThatThrownBy(() -> gateway.generate(
                new AiProviderRequest(AiFeature.DRAFT_REVIEW, "system", "user")
        ))
                .isInstanceOf(AiProviderException.class)
                .extracting(exception -> ((AiProviderException) exception).getErrorCode())
                .isEqualTo(errorCode);
    }

    private void assertStructuredError(
            SpringAiNvidiaGateway gateway,
            StructuredAiProviderRequest<TestOutput> request,
            ErrorCode errorCode
    ) {
        assertThatThrownBy(() -> gateway.generateStructured(request))
                .isInstanceOf(AiProviderException.class)
                .extracting(exception -> ((AiProviderException) exception).getErrorCode())
                .isEqualTo(errorCode);
    }

    private SpringAiNvidiaGateway gateway(
            ChatModel chatModel,
            Duration timeout,
            int maxTransportRetries,
            int maxCorrectionRetries
    ) {
        return gateway(
                chatModel,
                timeout,
                maxTransportRetries,
                maxCorrectionRetries,
                new AiRetrySleeper() {
                    @Override
                    public void sleep(Duration duration) {
                        // 단위 테스트에서는 backoff를 기다리지 않는다.
                    }
                }
        );
    }

    private SpringAiNvidiaGateway gateway(
            ChatModel chatModel,
            Duration timeout,
            int maxTransportRetries,
            int maxCorrectionRetries,
            AiRetrySleeper retrySleeper
    ) {
        AiProperties properties = new AiProperties(
                "nvidia",
                new AiProperties.ModelOptions(
                        "test-model",
                        timeout,
                        100_000,
                        128,
                        131_072,
                        8_192,
                        AiProperties.ModelOptions.CONSERVATIVE_CHAR_V1,
                        AiProperties.ModelOptions.REJECT_OVERSIZED_INPUT_V1,
                        maxTransportRetries,
                        Duration.ZERO,
                        Duration.ofSeconds(8)
                ),
                Map.of(),
                new AiProperties.StructuredOutputOptions(maxCorrectionRetries)
        );
        ChatModel modelWithOptions = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return chatModel.call(prompt);
            }

            @Override
            public ChatOptions getOptions() {
                return defaultOptions();
            }
        };
        return new SpringAiNvidiaGateway(
                ChatClient.builder(modelWithOptions)
                        .defaultOptions(OpenAiChatOptions.builder()
                                .model("test-model")
                                .maxTokens(128)
                                .temperature(1.0d)
                                .maxRetries(0))
                        .build(),
                properties,
                new NvidiaProviderProperties(0.1d, "none"),
                new NvidiaExceptionTranslator(),
                executor,
                new AiUsageExtractor(),
                retrySleeper,
                new AiTokenBudgetEstimator(properties)
        );
    }

    private OpenAiChatOptions defaultOptions() {
        return OpenAiChatOptions.builder()
                .model("test-model")
                .maxTokens(128)
                .temperature(1.0d)
                .maxRetries(0)
                .build();
    }

    private StructuredAiProviderRequest<TestOutput> structuredRequest(Set<Long> allowedResourceIds) {
        StructuredOutputValidator<TestOutput> validator = (value, context) -> {
            if (!context.allowedResourceIds().contains(value.resourceId())) {
                throw new StructuredOutputSemanticException();
            }
        };
        return new StructuredAiProviderRequest<>(
                AiFeature.DRAFT_REVIEW,
                new RenderedPrompt(
                        new PromptTemplateId("contract-summary", 1),
                        "system",
                        "user"
                ),
                StructuredOutputDefinition.of(
                        new OutputSchemaId("test-output", 1),
                        TestOutput.class,
                        validator
                ),
                new StructuredOutputValidationContext(allowedResourceIds)
        );
    }

    private ChatResponse response(String content) {
        return response(content, "stop");
    }

    private ChatResponse response(String content, String finishReason) {
        Generation generation = new Generation(
                new AssistantMessage(content),
                ChatGenerationMetadata.builder().finishReason(finishReason).build()
        );
        return ChatResponse.builder()
                .generations(List.of(generation))
                .metadata(ChatResponseMetadata.builder()
                        .id("request-1")
                        .model("response-model")
                        .usage(new DefaultUsage(5, 2, 7))
                        .build())
                .build();
    }

    private enum TestSignal {
        CLEAR,
        PARTIAL
    }

    private record TestOutput(Long resourceId, TestSignal signal) {
    }

    private static final class TestServiceException extends OpenAIServiceException {

        private final int statusCode;
        private final String retryAfter;

        private TestServiceException(int statusCode) {
            this(statusCode, null);
        }

        private TestServiceException(int statusCode, String retryAfter) {
            super("provider error", null);
            this.statusCode = statusCode;
            this.retryAfter = retryAfter;
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public Headers headers() {
            Headers.Builder builder = Headers.builder();
            if (retryAfter != null) {
                builder.put("retry-after", retryAfter);
            }
            return builder.build();
        }

        @Override
        public JsonValue body() {
            return JsonValue.from(Map.of());
        }

        @Override
        public Optional<String> code() {
            return Optional.empty();
        }

        @Override
        public Optional<String> param() {
            return Optional.empty();
        }

        @Override
        public Optional<String> type() {
            return Optional.empty();
        }
    }
}
