package com.wevo.backend.ai.context;

/** 섹션 템플릿의 Provider 입력 허용 정보. */
public record AiTemplateContext(
        String templateKey,
        String description,
        String guide
) {
}
