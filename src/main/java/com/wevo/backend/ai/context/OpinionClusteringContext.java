package com.wevo.backend.ai.context;

import com.wevo.backend.ai.domain.AiFeature;
import java.util.List;

/** 제출 의견 자동 분류를 위한 provider 중립 입력. 사용자 실명·이메일은 포함하지 않는다. */
public record OpinionClusteringContext(
        Long projectId,
        Long sectionId,
        String sectionTitle,
        long opinionGateGeneration,
        AiTemplateContext template,
        List<AiOpinionContext> opinions
) implements AiFeatureContext {

    public OpinionClusteringContext {
        if (projectId == null || sectionId == null || opinionGateGeneration < 0
                || sectionTitle == null || sectionTitle.isBlank() || template == null
                || opinions == null) {
            throw new IllegalArgumentException("opinion clustering context가 유효하지 않습니다.");
        }
        opinions = List.copyOf(opinions);
    }

    @Override
    public AiFeature feature() {
        return AiFeature.OPINION_CLUSTERING;
    }

    @Override
    public String sourceVersion() {
        return "opinion-clustering-source:v1";
    }
}
