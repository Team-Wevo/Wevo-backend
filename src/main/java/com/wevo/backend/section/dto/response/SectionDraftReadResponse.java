package com.wevo.backend.section.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wevo.backend.section.domain.SectionDraft;
import java.time.LocalDateTime;

/**
 * 최신 초안 조회 응답.
 *
 * @param activeEditor 현재 편집 중인 사용자 — 아무도 편집권을 잡고 있지 않으면 {@code null} 이며
 *                     직렬화에서 제외된다. 화면의 "OO님 편집 중" 표시에 쓴다. (정책서 §5.2)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SectionDraftReadResponse(
        String content,
        Integer contentVersion,
        ActiveEditor activeEditor,
        LocalDateTime updatedAt
) {

    public static SectionDraftReadResponse of(SectionDraft draft, ActiveEditor activeEditor) {
        return new SectionDraftReadResponse(
                draft.getContent(),
                draft.getVersion(),
                activeEditor,
                draft.getUpdatedAt());
    }

    /**
     * 편집권을 보유한 사용자.
     */
    public record ActiveEditor(Long userId, String name) {
    }
}
