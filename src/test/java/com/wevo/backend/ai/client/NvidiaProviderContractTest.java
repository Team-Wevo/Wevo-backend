package com.wevo.backend.ai.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.exception.AiProviderException;
import com.wevo.backend.ai.prompt.PromptTemplateId;
import com.wevo.backend.ai.prompt.RenderedPrompt;
import com.wevo.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.extension.ExtendWith;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-nvidia-api-key",
        "spring.ai.openai.timeout=5s",
        "spring.ai.openai.max-retries=0",
        "wevo.ai.default-options.timeout=5s",
        "wevo.ai.default-options.max-retries=0"
})
@ExtendWith(OutputCaptureExtension.class)
class NvidiaProviderContractTest {

    private static final BlockingQueue<StubResponse> RESPONSES = new LinkedBlockingQueue<>();
    private static final BlockingQueue<CapturedRequest> REQUESTS = new LinkedBlockingQueue<>();
    private static final HttpServer SERVER = startServer();

    @Autowired
    private AiProviderGateway providerGateway;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void providerProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.ai.openai.base-url",
                () -> "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/v1"
        );
    }

    @BeforeEach
    void clearQueues() {
        RESPONSES.clear();
        REQUESTS.clear();
    }

    @AfterAll
    static void stopServer() {
        SERVER.stop(0);
    }

    @Test
    void sendsOpenAiCompatibleChatCompletionRequestAndMapsResponseMetadata() throws Exception {
        enqueue(200, successBody(true));

        AiProviderResponse response = providerGateway.generate(new AiProviderRequest(
                AiFeature.DRAFT_REVIEW,
                List.of(
                        new AiChatMessage(AiChatMessage.Role.SYSTEM, "system-contract"),
                        new AiChatMessage(AiChatMessage.Role.USER, "first-user"),
                        new AiChatMessage(AiChatMessage.Role.ASSISTANT, "first-assistant"),
                        new AiChatMessage(AiChatMessage.Role.USER, "second-user")
                )
        ));

        assertThat(response.content()).isEqualTo("contract-ok");
        assertThat(response.finishReason()).isEqualTo("STOP");
        assertThat(response.usageMetadata()).isEqualTo(new AiUsageMetadata(
                "nvidia", "chatcmpl-contract-1", "mistralai/mistral-medium-3.5-128b", 12L, 3L, null, null
        ));

        CapturedRequest captured = takeRequest();
        assertThat(captured.method()).isEqualTo("POST");
        assertThat(captured.path()).isEqualTo("/v1/chat/completions");
        assertThat(captured.headers().get("Authorization")).containsExactly("Bearer test-nvidia-api-key");
        assertThat(captured.headers().get("Content-type").getFirst()).contains("application/json");
        assertThat(captured.headers().get("Accept")).contains("application/json");

        JsonNode request = objectMapper.readTree(captured.body());
        assertThat(request.get("model").asText()).isEqualTo("test-model");
        assertThat(request.get("max_tokens").asInt()).isEqualTo(128);
        assertThat(request.get("n").asInt()).isEqualTo(1);
        assertThat(request.get("temperature").asDouble()).isEqualTo(0.1d);
        assertThat(request.get("reasoning_effort").asText()).isEqualTo("none");
        assertThat(request.get("response_format")).isNull();
        assertThat(request.get("messages")).extracting(
                message -> message.get("role").asText(),
                message -> message.get("content").asText()
        ).containsExactly(
                org.assertj.core.groups.Tuple.tuple("system", "system-contract"),
                org.assertj.core.groups.Tuple.tuple("user", "first-user"),
                org.assertj.core.groups.Tuple.tuple("assistant", "first-assistant"),
                org.assertj.core.groups.Tuple.tuple("user", "second-user")
        );
    }

    @Test
    void requestsMistralJsonModeForStructuredOutput() throws Exception {
        enqueue(200, structuredSuccessBody());

        StructuredAiProviderResponse<ContractSummary> response = providerGateway.generateStructured(
                new StructuredAiProviderRequest<>(
                        AiFeature.ISSUE_DETECTION,
                        new RenderedPrompt(
                                new PromptTemplateId("contract-summary", 1),
                                "Return only valid JSON.",
                                "Summarize the synthetic source."
                        ),
                        StructuredOutputDefinition.of(
                                new OutputSchemaId("contract-summary", 1),
                                ContractSummary.class
                        ),
                        StructuredOutputValidationContext.empty()
                )
        );

        assertThat(response.result()).isEqualTo(new ContractSummary("contract-ok"));

        JsonNode request = objectMapper.readTree(takeRequest().body());
        assertThat(request.get("reasoning_effort").asText()).isEqualTo("none");
        assertThat(request.get("response_format").get("type").asText()).isEqualTo("json_object");
        assertThat(request.get("messages").get(1).get("content").asText())
                .contains("<output_contract>", "summary");
    }

    @Test
    void preservesMissingUsageAsNullAndRejectsMissingChoicesOrContent() {
        enqueue(200, successBody(false));
        AiProviderResponse response = providerGateway.generate(request());
        assertThat(response.usageMetadata().inputTokens()).isNull();
        assertThat(response.usageMetadata().outputTokens()).isNull();

        enqueue(200, """
                {"id":"empty","object":"chat.completion","created":1,
                 "model":"mistralai/mistral-medium-3.5-128b","choices":[]}
                """);
        assertError(ErrorCode.AI_PROVIDER_INVALID_RESPONSE);

        enqueue(200, """
                {"id":"null-content","object":"chat.completion","created":1,
                 "model":"mistralai/mistral-medium-3.5-128b","choices":[
                   {"index":0,"message":{"role":"assistant","content":null},"finish_reason":"stop"}
                 ]}
                """);
        assertError(ErrorCode.AI_PROVIDER_INVALID_RESPONSE);
    }

    @Test
    void mapsProviderRefusalFieldWithoutPersistingItAsContent() {
        enqueue(200, """
                {"id":"refusal","object":"chat.completion","created":1,
                 "model":"mistralai/mistral-medium-3.5-128b","choices":[
                   {"index":0,"message":{"role":"assistant","content":null,"refusal":"not allowed"},
                    "finish_reason":"stop"}
                 ]}
                """);

        assertError(ErrorCode.AI_PROVIDER_REFUSAL);
    }

    @Test
    void doesNotLogPromptWhenProviderReturnsNoChoices(CapturedOutput output) {
        enqueue(200, """
                {"id":"empty","object":"chat.completion","created":1,
                 "model":"mistralai/mistral-medium-3.5-128b","choices":[]}
                """);

        assertThatThrownBy(() -> providerGateway.generate(new AiProviderRequest(
                AiFeature.DRAFT_REVIEW,
                "system",
                "sensitive-synthetic-prompt"
        ))).isInstanceOf(AiProviderException.class);

        assertThat(output).doesNotContain(
                "sensitive-synthetic-prompt",
                "test-nvidia-api-key",
                "Authorization: Bearer"
        );
    }

    @ParameterizedTest
    @CsvSource({
            "400, AI_INVALID_REQUEST",
            "401, AI_PROVIDER_AUTHENTICATION_FAILED",
            "403, AI_PROVIDER_PERMISSION_DENIED",
            "404, AI_MODEL_NOT_AVAILABLE",
            "422, AI_INVALID_REQUEST",
            "429, AI_RATE_LIMITED",
            "500, AI_PROVIDER_UNAVAILABLE"
    })
    void mapsProviderHttpErrorsWithoutLeakingResponseBody(int status, ErrorCode expected) {
        enqueue(status, """
                {"error":{"message":"provider-secret-body","type":"contract_error","code":"contract"}}
                """);

        assertThatThrownBy(() -> providerGateway.generate(request()))
                .isInstanceOf(AiProviderException.class)
                .satisfies(exception -> {
                    AiProviderException providerException = (AiProviderException) exception;
                    assertThat(providerException.getErrorCode()).isEqualTo(expected);
                    assertThat(providerException.getMessage()).doesNotContain("provider-secret-body");
                    assertThat(providerException.getAttemptCount()).isEqualTo(1);
                });
    }

    @Test
    void treatsMalformedAndPendingBodiesAsInvalidResponsesInsteadOfSuccess() {
        enqueue(200, "not-json");
        assertError(ErrorCode.AI_PROVIDER_INVALID_RESPONSE);

        enqueue(202, "{\"requestId\":\"pending-contract-request\"}");
        assertError(ErrorCode.AI_PROVIDER_INVALID_RESPONSE);
    }

    private void assertError(ErrorCode expected) {
        assertThatThrownBy(() -> providerGateway.generate(request()))
                .isInstanceOf(AiProviderException.class)
                .extracting(exception -> ((AiProviderException) exception).getErrorCode())
                .isEqualTo(expected);
    }

    private AiProviderRequest request() {
        return new AiProviderRequest(AiFeature.DRAFT_REVIEW, "system", "synthetic user input");
    }

    private static String successBody(boolean includeUsage) {
        String usage = includeUsage
                ? ",\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":3,\"total_tokens\":15}"
                : "";
        return """
                {"id":"chatcmpl-contract-1","object":"chat.completion","created":1,
                 "model":"mistralai/mistral-medium-3.5-128b","choices":[
                   {"index":0,"message":{"role":"assistant","content":"contract-ok"},"finish_reason":"stop"}
                 ]%s}
                """.formatted(usage);
    }

    private static String structuredSuccessBody() {
        return """
                {"id":"chatcmpl-structured-1","object":"chat.completion","created":1,
                 "model":"mistralai/mistral-medium-3.5-128b","choices":[
                   {"index":0,"message":{"role":"assistant","content":"{\\"summary\\":\\"contract-ok\\"}"},
                    "finish_reason":"stop"}
                 ],"usage":{"prompt_tokens":20,"completion_tokens":5,"total_tokens":25}}
                """;
    }

    private static void enqueue(int status, String body) {
        RESPONSES.add(new StubResponse(status, body));
    }

    private static CapturedRequest takeRequest() throws InterruptedException {
        CapturedRequest request = REQUESTS.poll(2, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        return request;
    }

    private static HttpServer startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", NvidiaProviderContractTest::handle);
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, List<String>> headers = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        exchange.getRequestHeaders().forEach((key, value) -> headers.put(key, new ArrayList<>(value)));
        REQUESTS.add(new CapturedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                Map.copyOf(headers),
                requestBody
        ));

        StubResponse response = RESPONSES.poll();
        if (response == null) {
            response = new StubResponse(500, "{\"error\":{\"message\":\"missing stub\"}}");
        }
        byte[] responseBody = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), responseBody.length);
        exchange.getResponseBody().write(responseBody);
        exchange.close();
    }

    private record StubResponse(int status, String body) {
    }

    private record CapturedRequest(
            String method,
            String path,
            Map<String, List<String>> headers,
            String body
    ) {
    }

    private record ContractSummary(String summary) {
    }
}
