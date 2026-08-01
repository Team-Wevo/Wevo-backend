package com.wevo.backend.ai.context;

import com.wevo.backend.ai.domain.AiFeature;

/** 현재 섹션 제목·template guide·최신 초안만 포함하는 작성자 의도 추출 입력. */
public record AuthorIntentContext(
        Long sectionId,
        String sectionTitle,
        String templateGuide,
        Long draftId,
        int contentVersion,
        String content
) implements AiFeatureContext {

    @Override
    public AiFeature feature() {
        return AiFeature.AUTHOR_INTENT_EXTRACTION;
    }

    @Override
    public String sourceVersion() {
        return "draft-v" + contentVersion;
    }
}
