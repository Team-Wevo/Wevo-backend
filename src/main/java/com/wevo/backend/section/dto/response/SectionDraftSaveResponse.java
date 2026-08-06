package com.wevo.backend.section.dto.response;

import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.domain.SectionDraft;
import com.wevo.backend.section.service.DriftedSection;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 섹션 초안 저장 응답. (API_SPEC §3.7.2)
 *
 * @param contentVersion 저장 후 새 본문 버전. 다음 저장 시 클라이언트가 {@code baseVersion} 으로 보낸다.
 * @param updatedAt      저장 시각
 * @param sectionStatus  저장 후 섹션 상태
 * @param driftedSections 확정 본문 변경으로 직접 영향받은 하위 섹션. 없으면 빈 배열
 */
@Schema(requiredProperties = {"contentVersion", "updatedAt", "sectionStatus", "driftedSections"},
        example = """
                {
                  "contentVersion": 4,
                  "updatedAt": "2026-08-06T18:20:00",
                  "sectionStatus": "DRAFTING",
                  "driftedSections": [
                    { "sectionId": 15, "title": "기대 효과", "sectionStatus": "REVIEWING", "driftStatus": "REVIEW_REQUIRED" }
                  ]
                }""")
public record SectionDraftSaveResponse(
        Integer contentVersion,
        LocalDateTime updatedAt,
        ProjectSectionStatus sectionStatus,
        List<DriftedSectionResponse> driftedSections
) {

    public SectionDraftSaveResponse {
        driftedSections = List.copyOf(driftedSections);
    }

    public static SectionDraftSaveResponse from(
            SectionDraft draft,
            ProjectSectionStatus sectionStatus,
            List<DriftedSection> driftedSections
    ) {
        return new SectionDraftSaveResponse(
                draft.getVersion(),
                draft.getUpdatedAt(),
                sectionStatus,
                driftedSections.stream().map(DriftedSectionResponse::from).toList()
        );
    }
}
