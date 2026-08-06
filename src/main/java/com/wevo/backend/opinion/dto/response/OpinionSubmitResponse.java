package com.wevo.backend.opinion.dto.response;

import com.wevo.backend.opinion.domain.Opinion;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 내 의견 제출 응답. (API_SPEC §3.4.3 — {@code id}/{@code submittedAt})
 *
 * @param submittedAt 최초 제출 시각 — 재제출해도 갱신되지 않는다 (§4.3 제출 이력)
 */
@Schema(requiredProperties = {"id", "submittedAt"})
public record OpinionSubmitResponse(
        Long id,
        LocalDateTime submittedAt
) {

    public static OpinionSubmitResponse from(Opinion opinion) {
        return new OpinionSubmitResponse(
                opinion.getId(),
                opinion.getSubmittedAt()
        );
    }
}
