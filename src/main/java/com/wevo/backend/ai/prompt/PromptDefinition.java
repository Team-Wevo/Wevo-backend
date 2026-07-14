package com.wevo.backend.ai.prompt;

import java.util.Set;

public record PromptDefinition(
        PromptTemplateId id,
        String systemTemplate,
        String userTemplate,
        Set<String> requiredVariables
) {

    public PromptDefinition {
        if (id == null) {
            throw new IllegalArgumentException("prompt id는 필수입니다.");
        }
        if (systemTemplate == null || systemTemplate.isBlank()) {
            throw new IllegalArgumentException("system template은 비어 있을 수 없습니다.");
        }
        if (userTemplate == null || userTemplate.isBlank()) {
            throw new IllegalArgumentException("user template은 비어 있을 수 없습니다.");
        }
        requiredVariables = requiredVariables == null ? Set.of() : Set.copyOf(requiredVariables);
    }
}
