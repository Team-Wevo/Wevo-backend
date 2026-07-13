package com.wevo.backend.opinion.dto.response;

import com.wevo.backend.opinion.domain.Opinion;
import com.wevo.backend.opinion.domain.OpinionStatus;
import java.time.LocalDateTime;

/**
 * 내 의견 임시저장 응답.
 */
public record OpinionDraftResponse(
        Long id,
        String content,
        OpinionStatus status,
        LocalDateTime updatedAt
) {

    public static OpinionDraftResponse from(Opinion opinion) {
        return new OpinionDraftResponse(
                opinion.getId(),
                opinion.getContent(),
                opinion.getStatus(),
                opinion.getUpdatedAt()
        );
    }
}
