package com.wevo.backend.ai.client;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiPromptCacheHttpInterceptorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenAiPromptCacheHttpInterceptor interceptor =
            new OpenAiPromptCacheHttpInterceptor(objectMapper);

    @Test
    void leavesImplicitRequestBytesUntouched() throws Exception {
        byte[] body = """
                {"messages":[{"role":"system","content":"stable"}]}
                """.getBytes(StandardCharsets.UTF_8);

        assertThat(interceptor.addExplicitBreakpoint(body)).isSameAs(body);
    }

    @Test
    void marksOnlyTheStableSystemContentForExplicitCaching() throws Exception {
        byte[] transformed = interceptor.addExplicitBreakpoint("""
                {"prompt_cache_options":{"mode":"explicit","ttl":"30m"},
                 "messages":[
                   {"role":"system","content":"stable-prefix"},
                   {"role":"user","content":"dynamic-suffix"}]}
                """.getBytes(StandardCharsets.UTF_8));

        JsonNode root = objectMapper.readTree(transformed);
        JsonNode content = root.path("messages").get(0).path("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content.get(0).path("text").asText()).isEqualTo("stable-prefix");
        assertThat(content.get(0).path("prompt_cache_breakpoint").path("mode").asText())
                .isEqualTo("explicit");
        assertThat(root.path("messages").get(1).path("content").asText())
                .isEqualTo("dynamic-suffix");
    }

    @Test
    void rejectsExplicitModeWhenTheExpectedStableBoundaryIsMissing() {
        assertThatThrownBy(() -> interceptor.addExplicitBreakpoint("""
                {"prompt_cache_options":{"mode":"explicit","ttl":"30m"},
                 "messages":[{"role":"user","content":"dynamic-only"}]}
                """.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("request shape");
    }
}
