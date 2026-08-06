package com.wevo.backend.section.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 섹션의 현재 초안 편집 잠금 상태.
 *
 * <p>필수는 {@code locked} 하나뿐이다 — 아무도 편집권을 잡고 있지 않으면 {@code editor} 와
 * {@code expiresAt} 이 비므로, FE 는 {@code locked} 로 먼저 분기해야 한다.
 */
@Schema(requiredProperties = {"locked"})
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

    @Schema(requiredProperties = {"userId", "name"})

    public record EditorResponse(Long userId, String name) {
    }
}
