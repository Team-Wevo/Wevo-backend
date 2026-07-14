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
}
