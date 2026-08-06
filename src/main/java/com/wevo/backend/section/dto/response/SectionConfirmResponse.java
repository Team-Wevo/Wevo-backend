package com.wevo.backend.section.dto.response;

import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 섹션 확정 응답. (API_SPEC §3.7.6)
 *
 * @param sectionId        섹션 ID
 * @param sectionStatus    전이 결과 ({@code CONFIRMED})
 * @param confirmedVersion 확정된 본문 버전
 */
@Schema(requiredProperties = {"sectionId", "sectionStatus", "confirmedVersion"})
public record SectionConfirmResponse(
        Long sectionId,
        ProjectSectionStatus sectionStatus,
        Integer confirmedVersion
) {

    public static SectionConfirmResponse from(ProjectSection section) {
        return new SectionConfirmResponse(
                section.getId(), section.getStatus(), section.getConfirmedVersion());
    }
}
