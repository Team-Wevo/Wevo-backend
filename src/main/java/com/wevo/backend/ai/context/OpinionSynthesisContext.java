package com.wevo.backend.ai.context;

import com.wevo.backend.ai.domain.AiFeature;
import java.util.List;

/** 제출 의견과 기존 GAP 보충 근거를 포함한 종합 입력. */
public record OpinionSynthesisContext(
        AiProjectIdentity project,
        AiProjectBrief projectBrief,
        AiSectionContext section,
        List<AiOpinionContext> opinions,
        AiSynthesisContext previousSynthesis,
        List<AiPrerequisiteContext> prerequisites
) implements AiFeatureContext {

    public OpinionSynthesisContext {
        opinions = List.copyOf(opinions);
        prerequisites = List.copyOf(prerequisites);
    }

    @Override
    public AiFeature feature() {
        return AiFeature.OPINION_SYNTHESIS;
    }

    @Override
    public String sourceVersion() {
        return "gate-g" + section.opinionGateGeneration()
                + "-base-s" + previousSynthesis.synthesisSetId();
    }
}
