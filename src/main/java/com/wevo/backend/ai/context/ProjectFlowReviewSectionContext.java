package com.wevo.backend.ai.context;

/** 흐름 점검에 사용하는 한 확정 섹션의 불변 입력. */
public record ProjectFlowReviewSectionContext(
        Long sectionId,
        String sectionKey,
        int order,
        int confirmedVersion,
        String title,
        AiTemplateContext template,
        String content
) {
    public ProjectFlowReviewSectionContext {
        if (sectionId == null || sectionId <= 0 || order <= 0 || confirmedVersion <= 0
                || sectionKey == null || sectionKey.isBlank()
                || title == null || title.isBlank() || template == null
                || content == null || content.isBlank()) {
            throw new IllegalArgumentException("확정 section 흐름 점검 입력이 유효하지 않습니다.");
        }
    }
}
