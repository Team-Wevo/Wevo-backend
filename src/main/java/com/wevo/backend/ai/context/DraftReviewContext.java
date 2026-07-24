package com.wevo.backend.ai.context;

import com.wevo.backend.ai.domain.AiFeature;
import java.util.List;

/** 자기 최신 초안과 직접 상위 최신본을 사용하는 AI 사전 검토 입력. */
public record DraftReviewContext(
        AiProjectIdentity project,
        AiSectionContext section,
        int contentVersion,
        String content,
        List<AiPrerequisiteContext> prerequisites,
        String dependencyVersionHash
) implements AiFeatureContext {

    public DraftReviewContext {
        prerequisites = List.copyOf(prerequisites);
    }

    @Override
    public AiFeature feature() {
        return AiFeature.DRAFT_REVIEW;
    }

    @Override
    public String sourceVersion() {
        return "draft-v" + contentVersion + "-deps-" + dependencyVersionHash;
    }
}
