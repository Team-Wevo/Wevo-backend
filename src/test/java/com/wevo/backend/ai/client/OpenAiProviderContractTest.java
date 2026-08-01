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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "wevo.ai.provider=openai",
        "wevo.ai.openai.api-key=test-openai-api-key",
        "wevo.ai.openai.model=gpt-5.6-luna",
        "wevo.ai.openai.timeout=5s",
        "wevo.ai.openai.max-output-tokens=256",
        "wevo.ai.openai.reasoning-effort=medium",
        "wevo.ai.default-options.max-retries=0",
        "wevo.ai.structured-output.max-correction-retries=0"
})
@ExtendWith(OutputCaptureExtension.class)
class OpenAiProviderContractTest {

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
                "wevo.ai.openai.base-url",
                () -> "http://127.0.0.1:" + SERVER.getAddress().getPort()
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
    void sendsLunaReasoningRequestWithoutTemperatureAndMapsNativeMetadata() throws Exception {
        enqueue(200, successBody());

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
        assertThat(response.usageMetadata()).isEqualTo(new AiUsageMetadata(
                "openai", "req-openai-contract", "gpt-5.6-luna-2026-07-29",
                9L, 7L, 3L, 0L, 4L
        ));

        CapturedRequest captured = takeRequest();
        assertThat(captured.path()).isEqualTo("/v1/chat/completions");
        assertThat(captured.headers().get("Authorization")).containsExactly("Bearer test-openai-api-key");
        JsonNode request = objectMapper.readTree(captured.body());
        assertThat(request.get("model").asText()).isEqualTo("gpt-5.6-luna");
        assertThat(request.get("max_completion_tokens").asInt()).isEqualTo(256);
        assertThat(request.get("reasoning_effort").asText()).isEqualTo("medium");
        assertThat(request.get("temperature")).isNull();
        assertThat(request.get("max_tokens")).isNull();
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
    void sendsNativeStrictJsonSchemaAndStillAppliesServerValidation() throws Exception {
        enqueue(200, structuredBody("{\"resourceId\":7,\"signal\":\"CLEAR\"}"));

        StructuredAiProviderResponse<ContractOutput> response = providerGateway.generateStructured(
                structuredRequest(Set.of(7L))
        );

        assertThat(response.result()).isEqualTo(new ContractOutput(7L, ContractSignal.CLEAR));
        JsonNode request = objectMapper.readTree(takeRequest().body());
        JsonNode responseFormat = request.get("response_format");
        assertThat(responseFormat.get("type").asText()).isEqualTo("json_schema");
        assertThat(responseFormat.get("json_schema").get("strict").asBoolean()).isTrue();
        assertThat(responseFormat.get("json_schema").get("schema").toString())
                .contains("resourceId", "signal");
        assertThat(request.get("messages").get(1).get("content").asText())
                .contains("<output_contract>", "resourceId", "signal");
    }

    @Test
    void rejectsMalformedMissingEnumExtraAndUnknownEvidenceThroughCommonPipeline() {
        enqueue(200, structuredBody("not-json"));
        assertStructuredError(ErrorCode.AI_STRUCTURED_OUTPUT_JSON_PARSE_FAILED, Set.of(7L));

        enqueue(200, structuredBody("{\"resourceId\":7}"));
        assertStructuredError(ErrorCode.AI_STRUCTURED_OUTPUT_SCHEMA_VALIDATION_FAILED, Set.of(7L));

        enqueue(200, structuredBody("{\"resourceId\":7,\"signal\":\"UNKNOWN\"}"));
        assertStructuredError(ErrorCode.AI_STRUCTURED_OUTPUT_SCHEMA_VALIDATION_FAILED, Set.of(7L));

        enqueue(200, structuredBody(
                "{\"resourceId\":7,\"signal\":\"CLEAR\",\"unexpected\":true}"
        ));
        assertStructuredError(ErrorCode.AI_STRUCTURED_OUTPUT_SCHEMA_VALIDATION_FAILED, Set.of(7L));

        enqueue(200, structuredBody("{\"resourceId\":8,\"signal\":\"CLEAR\"}"));
        assertStructuredError(ErrorCode.AI_STRUCTURED_OUTPUT_SEMANTIC_VALIDATION_FAILED, Set.of(7L));
    }

    @Test
    void rejectsRefusalLengthMissingChoicesBlankAndUnknownFinishReason() {
        enqueue(200, responseBody(null, "stop", "policy refusal"));
        assertError(ErrorCode.AI_PROVIDER_REFUSAL);
        enqueue(200, responseBody("filtered", "content_filter", null));
        assertError(ErrorCode.AI_PROVIDER_REFUSAL);
        enqueue(200, responseBody("partial", "length", null));
        assertError(ErrorCode.AI_PROVIDER_MAX_TOKENS);
        enqueue(200, "{\"id\":\"empty\",\"model\":\"gpt-5.6-luna\",\"choices\":[]}");
        assertError(ErrorCode.AI_PROVIDER_INVALID_RESPONSE);
        enqueue(200, responseBody(" ", "stop", null));
        assertError(ErrorCode.AI_PROVIDER_INVALID_RESPONSE);
        enqueue(200, responseBody("tool", "tool_calls", null));
        assertError(ErrorCode.AI_PROVIDER_INVALID_RESPONSE);
    }

    @ParameterizedTest
    @CsvSource({
            "400, AI_INVALID_REQUEST",
            "401, AI_PROVIDER_AUTHENTICATION_FAILED",
            "403, AI_PROVIDER_PERMISSION_DENIED",
            "404, AI_MODEL_NOT_AVAILABLE",
            "422, AI_INVALID_REQUEST",
            "429, AI_RATE_LIMITED",
            "500, AI_PROVIDER_UNAVAILABLE",
            "503, AI_PROVIDER_UNAVAILABLE",
            "504, AI_PROVIDER_TIMEOUT"
    })
    void mapsHttpErrorsWithoutLeakingProviderBodyOrCredentials(
            int status,
            ErrorCode expected,
            CapturedOutput output
    ) {
        enqueue(status, "{\"error\":{\"message\":\"provider-secret-body\"}}");

        assertThatThrownBy(() -> providerGateway.generate(request()))
                .isInstanceOf(AiProviderException.class)
                .satisfies(exception -> {
                    AiProviderException providerException = (AiProviderException) exception;
                    assertThat(providerException.getErrorCode()).isEqualTo(expected);
                    assertThat(providerException.getMessage()).doesNotContain("provider-secret-body");
                });
        assertThat(output).doesNotContain(
                "provider-secret-body", "test-openai-api-key", "Authorization: Bearer"
        );
    }

    private StructuredAiProviderRequest<ContractOutput> structuredRequest(Set<Long> allowedIds) {
        return new StructuredAiProviderRequest<>(
                AiFeature.ISSUE_DETECTION,
                new RenderedPrompt(
                        new PromptTemplateId("contract-output", 1),
                        "Return only the requested structured output.",
                        "Use the synthetic resource."
                ),
                StructuredOutputDefinition.of(
                        new OutputSchemaId("contract-output", 1),
                        ContractOutput.class,
                        (output, context) -> {
                            if (!context.allowedResourceIds().contains(output.resourceId())) {
                                throw new StructuredOutputSemanticException();
                            }
                        }
                ),
                new StructuredOutputValidationContext(allowedIds)
        );
    }

    private void assertStructuredError(ErrorCode expected, Set<Long> allowedIds) {
        assertThatThrownBy(() -> providerGateway.generateStructured(structuredRequest(allowedIds)))
                .isInstanceOf(AiProviderException.class)
                .extracting(exception -> ((AiProviderException) exception).getErrorCode())
                .isEqualTo(expected);
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

    private static String successBody() {
        return """
                {"id":"chatcmpl-contract","request_id":"req-openai-contract","created":1,
                 "model":"gpt-5.6-luna-2026-07-29","choices":[
                   {"index":0,"message":{"role":"assistant","content":"contract-ok"},"finish_reason":"stop"}
                 ],"usage":{"prompt_tokens":12,"completion_tokens":7,"total_tokens":19,
                   "prompt_tokens_details":{"cached_tokens":3,"cache_write_tokens":0},
                   "completion_tokens_details":{"reasoning_tokens":4}}}
                """;
    }

    private static String structuredBody(String jsonContent) {
        return responseBody(jsonContent, "stop", null);
    }

    private static String responseBody(String content, String finishReason, String refusal) {
        String encodedContent = content == null ? "null" : "\"" + escape(content) + "\"";
        String encodedRefusal = refusal == null ? "null" : "\"" + escape(refusal) + "\"";
        return """
                {"id":"chatcmpl-structured","created":1,"model":"gpt-5.6-luna","choices":[
                  {"index":0,"message":{"role":"assistant","content":%s,"refusal":%s},
                   "finish_reason":"%s"}],
                 "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
                """.formatted(encodedContent, encodedRefusal, finishReason);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
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
            server.createContext("/", OpenAiProviderContractTest::handle);
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
                exchange.getRequestURI().getPath(), Map.copyOf(headers), requestBody
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

    private enum ContractSignal { CLEAR, UNCLEAR }

    private record ContractOutput(Long resourceId, ContractSignal signal) {
    }

    private record StubResponse(int status, String body) {
    }

    private record CapturedRequest(String path, Map<String, List<String>> headers, String body) {
    }
}
