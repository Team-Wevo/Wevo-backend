package com.wevo.backend.section.dto.response;

import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;

/**
 * 검토 요청 응답. (API_SPEC §3.7.7)
 *
 * @param sectionId     섹션 ID
 * @param sectionStatus 전이 결과 ({@code REVIEWING})
 */
public record SectionReviewRequestResponse(
        Long sectionId,
        ProjectSectionStatus sectionStatus
) {

    public static SectionReviewRequestResponse from(ProjectSection section) {
        return new SectionReviewRequestResponse(section.getId(), section.getStatus());
    }
}
