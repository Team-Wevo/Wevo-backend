package com.wevo.backend.section.dto.response;

import java.time.LocalDateTime;

/**
 * 섹션의 현재 초안 편집 잠금 상태.
 */
public record DraftLeaseStatusResponse(
        boolean locked,
        EditorResponse editor,
        LocalDateTime expiresAt
) {

    public static DraftLeaseStatusResponse locked(
            Long userId,
            String name,
            LocalDateTime expiresAt
    ) {
        return new DraftLeaseStatusResponse(
                true,
                new EditorResponse(userId, name),
                expiresAt
        );
    }

    public static DraftLeaseStatusResponse unlocked() {
        return new DraftLeaseStatusResponse(false, null, null);
    }

    public record EditorResponse(Long userId, String name) {
    }
}
