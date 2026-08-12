package com.wevo.backend.ai.prompt;

import com.wevo.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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
        assertThatThrownBy(() -> renderer.render(
                definition,
                Map.of("sourceText", "x".repeat(PromptRenderer.MAX_VARIABLE_LENGTH + 1))))
                .isInstanceOf(PromptException.class)
                .extracting(exception -> ((PromptException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_INPUT_BUDGET_EXCEEDED);
    }

    @Test
    void replacesPlaceholdersOnceRegardlessOfVariableIterationOrder() {
        PromptDefinition definition = new PromptDefinition(
                new PromptTemplateId("single-pass", 1),
                "System: {{firstValue}} / {{secondValue}}",
                "User: {{secondValue}} / {{firstValue}}",
                Set.of("firstValue", "secondValue")
        );
        Map<String, String> firstOrder = new LinkedHashMap<>();
        firstOrder.put("firstValue", "literal $5 \\ {{secondValue}}");
        firstOrder.put("secondValue", "actual value");
        Map<String, String> secondOrder = new LinkedHashMap<>();
        secondOrder.put("secondValue", "actual value");
        secondOrder.put("firstValue", "literal $5 \\ {{secondValue}}");

        RenderedPrompt firstRendered = renderer.render(definition, firstOrder);
        RenderedPrompt secondRendered = renderer.render(definition, secondOrder);

        assertThat(firstRendered).isEqualTo(secondRendered);
        assertThat(firstRendered.systemPrompt())
                .isEqualTo("System: literal $5 \\ {{secondValue}} / actual value");
        assertThat(firstRendered.userPrompt())
                .isEqualTo("User: actual value / literal $5 \\ {{secondValue}}");
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
