package com.wevo.backend.ai.prompt;

import com.wevo.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PromptRenderer {

    static final int MAX_VARIABLE_LENGTH = 100_000;
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([a-z][A-Za-z0-9]*)}}");

    public RenderedPrompt render(PromptDefinition definition, Map<String, String> variables) {
        if (definition == null) {
            throw new IllegalArgumentException("prompt definition은 필수입니다.");
        }
        Map<String, String> values = variables == null ? Map.of() : variables;
        validateVariableNames(definition.requiredVariables(), values.keySet());
        values.forEach(this::validateValue);

        return new RenderedPrompt(
                definition.id(),
                renderTemplate(definition.systemTemplate(), values),
                renderTemplate(definition.userTemplate(), values)
        );
    }

    private void validateVariableNames(Set<String> required, Set<String> provided) {
        Set<String> missing = new LinkedHashSet<>(required);
        missing.removeAll(provided);
        Set<String> unexpected = new LinkedHashSet<>(provided);
        unexpected.removeAll(required);
        if (!missing.isEmpty() || !unexpected.isEmpty()) {
            throw new PromptException(ErrorCode.AI_PROMPT_VARIABLE_INVALID);
        }
    }

    private void validateValue(String variable, String value) {
        if (value != null && value.length() > MAX_VARIABLE_LENGTH) {
            throw new PromptException(ErrorCode.AI_INPUT_BUDGET_EXCEEDED);
        }
        if (value == null || value.isBlank()) {
            throw new PromptException(ErrorCode.AI_PROMPT_VARIABLE_INVALID);
        }
    }

    private String renderTemplate(String template, Map<String, String> values) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder rendered = new StringBuilder(template.length());
        while (matcher.find()) {
            String replacement = escapeXml(values.get(matcher.group(1)));
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private String escapeXml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
