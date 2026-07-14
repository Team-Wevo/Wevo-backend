package com.wevo.backend.ai.prompt;

public record RenderedPrompt(
        PromptTemplateId id,
        String systemPrompt,
        String userPrompt
) {

    public RenderedPrompt {
        if (id == null) {
            throw new IllegalArgumentException("prompt id는 필수입니다.");
        }
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new IllegalArgumentException("systemPrompt는 필수입니다.");
        }
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException("userPrompt는 필수입니다.");
        }
    }

    public String trackingVersion() {
        return id.trackingValue();
    }
}
