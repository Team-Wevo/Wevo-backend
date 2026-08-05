package com.wevo.backend.ai.client;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;

/**
 * Spring AI 2.0이 아직 노출하지 않는 GPT-5.6 content breakpoint를 OpenAI wire 경계에서만 추가한다.
 */
@Component
@ConditionalOnProperty(prefix = "wevo.ai", name = "provider", havingValue = "openai")
public class OpenAiPromptCacheHttpInterceptor implements Interceptor {

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private final ObjectMapper objectMapper;

    public OpenAiPromptCacheHttpInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        RequestBody body = request.body();
        if (body == null || !"POST".equals(request.method())
                || !request.url().encodedPath().endsWith(CHAT_COMPLETIONS_PATH)) {
            return chain.proceed(request);
        }

        Buffer buffer = new Buffer();
        body.writeTo(buffer);
        byte[] original = buffer.readByteArray();
        byte[] transformed = addExplicitBreakpoint(original);
        if (transformed == original) {
            return chain.proceed(request);
        }

        MediaType contentType = body.contentType();
        Request rewritten = request.newBuilder()
                .removeHeader("Content-Length")
                .method(request.method(), RequestBody.Companion.create(transformed, contentType))
                .build();
        return chain.proceed(rewritten);
    }

    byte[] addExplicitBreakpoint(byte[] body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        JsonNode cacheOptions = root == null ? null : root.get("prompt_cache_options");
        if (cacheOptions == null || !"explicit".equals(cacheOptions.path("mode").asString())) {
            return body;
        }

        JsonNode messages = root.get("messages");
        if (!(messages instanceof ArrayNode messageArray)) {
            throw invalidShape();
        }
        ObjectNode systemMessage = null;
        for (JsonNode message : messageArray) {
            if (message instanceof ObjectNode object
                    && "system".equals(object.path("role").asString())) {
                systemMessage = object;
                break;
            }
        }
        if (systemMessage == null || !systemMessage.path("content").isString()) {
            throw invalidShape();
        }

        ObjectNode breakpoint = objectMapper.createObjectNode();
        breakpoint.put("mode", "explicit");
        ObjectNode contentPart = objectMapper.createObjectNode();
        contentPart.put("type", "text");
        contentPart.put("text", systemMessage.path("content").asString());
        contentPart.set("prompt_cache_breakpoint", breakpoint);
        ArrayNode content = objectMapper.createArrayNode();
        content.add(contentPart);
        systemMessage.set("content", content);
        return objectMapper.writeValueAsBytes(root);
    }

    private IOException invalidShape() {
        return new IOException("OpenAI explicit prompt cache request shape is invalid");
    }
}
