package com.wevo.backend.opinion.dto.response;

import com.wevo.backend.opinion.domain.Opinion;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 내 의견 임시저장 응답. (API_SPEC §3.4.2 — {@code id}/{@code content}/{@code updatedAt})
 */
@Schema(requiredProperties = {"id", "content", "updatedAt"})
public record OpinionDraftResponse(
        Long id,
        String content,
        LocalDateTime updatedAt
) {

    public static OpinionDraftResponse from(Opinion opinion) {
        return new OpinionDraftResponse(
                opinion.getId(),
                opinion.getContent(),
                opinion.getUpdatedAt()
        );
    }
}
