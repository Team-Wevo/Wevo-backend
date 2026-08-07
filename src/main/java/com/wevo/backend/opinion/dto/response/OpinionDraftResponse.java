package com.wevo.backend.opinion.dto.response;

import com.wevo.backend.opinion.domain.Opinion;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 내 의견 임시저장 응답. (API_SPEC §3.4.2 — {@code id}/{@code content}/{@code updatedAt})
 */
@Schema(requiredProperties = {"id", "content", "updatedAt"},
        example = """
                {
                  "id": 41,
                  "content": "우리 팀이 겪는 문제는 회의 뒤 결정이 어디에도 남지 않는다는 점입니다.",
                  "updatedAt": "2026-08-06T18:10:00"
                }""")
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
