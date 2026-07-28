package com.wevo.backend.ai.dto.model;

import com.wevo.backend.ai.domain.AiSectionFindingType;

/** AI 사전 검토의 개별 finding 출력. API DTO와 영속 엔티티로 직접 사용하지 않는다. */
public record DraftReviewFindingOutput(
        AiSectionFindingType type,
        String targetExcerpt,
        String comment,
        String suggestion
) {
}
