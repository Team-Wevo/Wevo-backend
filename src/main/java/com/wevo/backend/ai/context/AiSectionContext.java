package com.wevo.backend.ai.context;

import com.wevo.backend.section.domain.ProjectSectionStatus;

/** 현재 섹션과 템플릿의 Provider 입력 허용 정보. */
public record AiSectionContext(
        Long sectionId,
        String title,
        int sectionOrder,
        ProjectSectionStatus status,
        long opinionGateGeneration,
        boolean synthesisStale,
        AiTemplateContext template
) {
}
