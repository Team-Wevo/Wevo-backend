package com.wevo.backend.section.dto.response;

import com.wevo.backend.section.domain.SectionDraft;
import java.time.LocalDateTime;

/**
 * 섹션 초안 저장 응답.
 *
 * @param sectionId      섹션 ID
 * @param contentVersion 저장 후 새 본문 버전. 다음 저장 시 클라이언트가 {@code baseVersion} 으로 보낸다.
 * @param updatedAt      저장 시각
 */
public record SectionDraftSaveResponse(
        Long sectionId,
        Integer contentVersion,
        LocalDateTime updatedAt
) {

    public static SectionDraftSaveResponse from(Long sectionId, SectionDraft draft) {
        return new SectionDraftSaveResponse(sectionId, draft.getVersion(), draft.getUpdatedAt());
    }
}
