package com.wevo.backend.section.dto.response;

import com.wevo.backend.section.domain.DriftStatus;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.service.DriftedSection;
import io.swagger.v3.oas.annotations.media.Schema;

/** 확정 상위 본문 변경으로 직접 영향받은 하위 섹션 응답. */
@Schema(requiredProperties = {"sectionId", "title", "sectionStatus", "driftStatus"})
public record DriftedSectionResponse(
        Long sectionId,
        String title,
        ProjectSectionStatus sectionStatus,
        DriftStatus driftStatus
) {

    public static DriftedSectionResponse from(DriftedSection section) {
        return new DriftedSectionResponse(
                section.sectionId(),
                section.title(),
                section.sectionStatus(),
                section.driftStatus()
        );
    }
}
