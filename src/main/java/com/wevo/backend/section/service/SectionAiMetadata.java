package com.wevo.backend.section.service;

import com.wevo.backend.section.domain.ProjectSectionStatus;

/** AI 입력 조립에 허용된 section/template 최소 정보. */
public record SectionAiMetadata(
        Long sectionId,
        Long projectId,
        String title,
        int sectionOrder,
        ProjectSectionStatus status,
        long opinionGateGeneration,
        boolean synthesisStale,
        String templateKey,
        String templateDescription,
        String templateGuide
) {
}
