package com.wevo.backend.opinion.dto.response;

import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import java.time.LocalDateTime;

/** 의견 수집 재오픈 응답. */
public record OpinionGateReopenResponse(
        Long id,
        ProjectSectionStatus sectionStatus,
        boolean synthesisStale,
        LocalDateTime reopenedAt
) {

    public static OpinionGateReopenResponse from(ProjectSection section, LocalDateTime reopenedAt) {
        return new OpinionGateReopenResponse(
                section.getId(), section.getStatus(), section.isSynthesisStale(), reopenedAt);
    }
}
