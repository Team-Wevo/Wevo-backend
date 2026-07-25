package com.wevo.backend.ai.context;

/** 기능별 불변 context와 canonical snapshot 조립 결과. */
public record AssembledAiContext<T extends AiFeatureContext>(
        T context,
        AiInputSnapshot snapshot
) {

    public AssembledAiContext {
        if (context == null || snapshot == null) {
            throw new IllegalArgumentException("AI context와 snapshot은 필수입니다.");
        }
        if (context.sourceVersion() == null
                || context.sourceVersion().isBlank()
                || context.sourceVersion().length() > 100) {
            throw new IllegalArgumentException("AI context sourceVersion은 1자 이상 100자 이하여야 합니다.");
        }
    }
}
