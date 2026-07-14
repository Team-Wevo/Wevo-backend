package com.wevo.backend.ai.prompt;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PromptTemplateParser {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([a-z][A-Za-z0-9]*)}}", Pattern.MULTILINE);
    private static final Pattern ANY_PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{.*?}}", Pattern.DOTALL);

    private PromptTemplateParser() {
    }

    static Set<String> extractVariables(String template) {
        LinkedHashSet<String> variables = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        while (matcher.find()) {
            String variable = matcher.group(1);
            validateDelimiter(template, variable, matcher.start(), matcher.end());
            variables.add(variable);
        }

        String withoutValidPlaceholders = PLACEHOLDER_PATTERN.matcher(template).replaceAll("");
        if (ANY_PLACEHOLDER_PATTERN.matcher(withoutValidPlaceholders).find()
                || withoutValidPlaceholders.contains("{{")
                || withoutValidPlaceholders.contains("}}")) {
            throw new IllegalArgumentException("지원하지 않는 prompt placeholder가 있습니다.");
        }
        return Set.copyOf(variables);
    }

    private static void validateDelimiter(String template, String variable, int placeholderStart, int placeholderEnd) {
        int openingTagStart = template.lastIndexOf("<data", placeholderStart);
        int openingTagEnd = openingTagStart < 0 ? -1 : template.indexOf('>', openingTagStart);
        int closingTagStart = template.indexOf("</data>", placeholderEnd);
        if (openingTagStart < 0 || openingTagEnd < 0 || closingTagStart < 0
                || openingTagEnd > placeholderStart
                || template.lastIndexOf("</data>", placeholderStart) > openingTagStart) {
            throw new IllegalArgumentException("prompt 변수는 data delimiter 안에 있어야 합니다: " + variable);
        }

        String openingTag = template.substring(openingTagStart, openingTagEnd + 1);
        if (!openingTag.matches("<data\\s+name=\\\"" + Pattern.quote(variable) + "\\\">")) {
            throw new IllegalArgumentException("data delimiter 이름과 prompt 변수가 일치해야 합니다: " + variable);
        }
        if (!template.substring(openingTagEnd + 1, placeholderStart).isBlank()
                || !template.substring(placeholderEnd, closingTagStart).isBlank()) {
            throw new IllegalArgumentException("data delimiter에는 prompt 변수만 포함할 수 있습니다: " + variable);
        }
    }
}
