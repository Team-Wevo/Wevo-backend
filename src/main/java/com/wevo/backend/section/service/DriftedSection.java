package com.wevo.backend.section.service;

import com.wevo.backend.section.domain.DriftStatus;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;

/** 확정 상위 본문 변경으로 직접 영향받은 하위 섹션. */
public record DriftedSection(
        Long sectionId,
        String title,
        ProjectSectionStatus sectionStatus,
        DriftStatus driftStatus
) {

    static DriftedSection from(ProjectSection section) {
        return new DriftedSection(
                section.getId(),
                section.getTitle(),
                section.getStatus(),
                section.getDriftStatus()
        );
    }
}
