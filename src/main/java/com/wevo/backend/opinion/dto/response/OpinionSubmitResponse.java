package com.wevo.backend.opinion.dto.response;

import com.wevo.backend.opinion.domain.Opinion;
import com.wevo.backend.opinion.domain.OpinionStatus;
import java.time.LocalDateTime;

/**
 * 내 의견 제출 응답.
 */
public record OpinionSubmitResponse(
        Long id,
        OpinionStatus status,
        LocalDateTime submittedAt
) {

    public static OpinionSubmitResponse from(Opinion opinion) {
        return new OpinionSubmitResponse(
                opinion.getId(),
                opinion.getStatus(),
                opinion.getSubmittedAt()
        );
    }
}
