package com.wevo.backend.ai.context;

/** 직접 의존 상위 섹션에서 실제로 사용한 본문과 version. */
public record AiPrerequisiteContext(
        Long sectionId,
        String templateKey,
        int sectionOrder,
        int contentVersion,
        String content
) {
}
