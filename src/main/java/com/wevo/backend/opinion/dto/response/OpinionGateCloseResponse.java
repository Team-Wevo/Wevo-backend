package com.wevo.backend.opinion.dto.response;

import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import java.time.LocalDateTime;

/** 의견 수집 마감 응답. */
public record OpinionGateCloseResponse(
        ProjectSectionStatus sectionStatus,
        LocalDateTime closedAt
) {

    public static OpinionGateCloseResponse from(ProjectSection section, LocalDateTime closedAt) {
        return new OpinionGateCloseResponse(section.getStatus(), closedAt);
    }
}
