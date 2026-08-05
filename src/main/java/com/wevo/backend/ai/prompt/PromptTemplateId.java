package com.wevo.backend.ai.prompt;

import java.util.regex.Pattern;

public record PromptTemplateId(String promptName, int version) {

    private static final Pattern PROMPT_NAME_PATTERN = Pattern.compile("[a-z][a-z0-9]*(?:-[a-z0-9]+)*");

    public PromptTemplateId {
        if (promptName == null || !PROMPT_NAME_PATTERN.matcher(promptName).matches()) {
            throw new IllegalArgumentException("promptName은 kebab-case여야 합니다.");
        }
        if (version <= 0) {
            throw new IllegalArgumentException("prompt version은 1 이상이어야 합니다.");
        }
    }

    public String versionDirectory() {
        return "v" + version;
    }

    public String trackingValue() {
        return promptName + ":" + versionDirectory();
    }

    public static PromptTemplateId parseTrackingValue(String value) {
        if (value == null || !value.matches("[a-z][a-z0-9]*(?:-[a-z0-9]+)*:v[1-9][0-9]*")) {
            throw new IllegalArgumentException("prompt tracking version 형식이 올바르지 않습니다.");
        }
        int separator = value.lastIndexOf(":v");
        return new PromptTemplateId(
                value.substring(0, separator),
                Integer.parseInt(value.substring(separator + 2))
        );
    }
}
