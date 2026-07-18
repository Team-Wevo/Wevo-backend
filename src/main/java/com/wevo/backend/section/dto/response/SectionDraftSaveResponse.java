package com.wevo.backend.section.dto.response;

import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.domain.SectionDraft;
import java.time.LocalDateTime;

/**
 * 섹션 초안 저장 응답. (API_SPEC §3.7.2)
 *
 * <p>{@code driftedSections[]} 는 확정 섹션 수정에 따른 드리프트 전파(정책서 §6.4)와 함께
 * 추가할 예정이다.
 *
 * @param contentVersion 저장 후 새 본문 버전. 다음 저장 시 클라이언트가 {@code baseVersion} 으로 보낸다.
 * @param updatedAt      저장 시각
 * @param sectionStatus  저장 후 섹션 상태
 */
public record SectionDraftSaveResponse(
        Integer contentVersion,
        LocalDateTime updatedAt,
        ProjectSectionStatus sectionStatus
) {

    public static SectionDraftSaveResponse from(SectionDraft draft, ProjectSectionStatus sectionStatus) {
        return new SectionDraftSaveResponse(draft.getVersion(), draft.getUpdatedAt(), sectionStatus);
    }
}
