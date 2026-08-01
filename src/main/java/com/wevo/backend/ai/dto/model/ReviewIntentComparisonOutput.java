package com.wevo.backend.ai.dto.model;

import com.wevo.backend.review.domain.ReviewIntentAlignment;

/** 작성자 의도와 검토자 이해 문장의 구조화 비교 출력. */
public record ReviewIntentComparisonOutput(
        ReviewIntentAlignment alignment,
        String differenceSummary,
        String evidenceExcerpt
) {
}
