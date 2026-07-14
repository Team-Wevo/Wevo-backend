package com.wevo.backend.ai.prompt;

import com.wevo.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptRendererTest {

    private final PromptRegistry registry = new PromptRegistry(new PromptResourceLoader());
    private final PromptRenderer renderer = new PromptRenderer();

    @Test
    void rendersSnapshotInSystemUserOrderAndEscapesXml() throws IOException {
        PromptDefinition definition = registry.get(new PromptTemplateId("contract-summary", 1));

        RenderedPrompt rendered = renderer.render(
                definition,
                Map.of("sourceText", "Tom & Jerry <script>\"quoted\"'s")
        );

        assertThat(rendered.systemPrompt()).isEqualTo(resource("expected-system.txt"));
        assertThat(rendered.userPrompt()).isEqualTo(resource("expected-user.txt"));
        assertThat(rendered.trackingVersion()).isEqualTo("contract-summary:v1");
    }

    @Test
    void rejectsMissingAndUnexpectedVariablesWithoutExposingValues() {
        PromptDefinition definition = registry.get(new PromptTemplateId("contract-summary", 1));

        assertInvalid(() -> renderer.render(definition, Map.of()));
        assertInvalid(() -> renderer.render(definition, Map.of(
                "sourceText", "secret source",
                "unexpected", "secret unexpected"
        )));
    }

    @Test
    void rejectsNullBlankAndOversizedValues() {
        PromptDefinition definition = registry.get(new PromptTemplateId("contract-summary", 1));

        assertInvalid(() -> renderer.render(definition, java.util.Collections.singletonMap("sourceText", null)));
        assertInvalid(() -> renderer.render(definition, Map.of("sourceText", "   ")));
        assertInvalid(() -> renderer.render(
                definition,
                Map.of("sourceText", "x".repeat(PromptRenderer.MAX_VARIABLE_LENGTH + 1))
        ));
    }

    private String resource(String fileName) throws IOException {
        String path = "/prompts/ai/contract-summary/v1/" + fileName;
        try (var input = getClass().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("snapshot resource가 없습니다: " + fileName);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(PromptException.class)
                .hasMessage(ErrorCode.AI_PROMPT_VARIABLE_INVALID.getMessage())
                .hasMessageNotContaining("secret");
    }
}
