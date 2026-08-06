package com.wevo.backend.opinion.dto.response;

import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 의견 수집 마감 응답. */
@Schema(requiredProperties = {"sectionId", "sectionStatus", "closedAt"})
public record OpinionGateCloseResponse(
        Long sectionId,
        ProjectSectionStatus sectionStatus,
        LocalDateTime closedAt
) {

    public static OpinionGateCloseResponse from(ProjectSection section, LocalDateTime closedAt) {
        return new OpinionGateCloseResponse(section.getId(), section.getStatus(), closedAt);
    }
}
