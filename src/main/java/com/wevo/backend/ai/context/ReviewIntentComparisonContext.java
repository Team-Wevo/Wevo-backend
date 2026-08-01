package com.wevo.backend.ai.context;

import com.wevo.backend.ai.domain.AiFeature;

/** 링크에 고정된 의도와 단일 검토 제출의 이해 문장만 포함하는 비교 입력. */
public record ReviewIntentComparisonContext(
        Long submissionId,
        Long sectionId,
        int contentVersion,
        String authorIntent,
        String reviewerSummary
) implements AiFeatureContext {

    @Override
    public AiFeature feature() {
        return AiFeature.REVIEW_INTENT_COMPARISON;
    }

    @Override
    public String sourceVersion() {
        return "review-submission-" + submissionId + "-draft-v" + contentVersion;
    }
}
