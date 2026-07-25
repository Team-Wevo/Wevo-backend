package com.wevo.backend.ai.context;

import com.wevo.backend.ai.domain.AiFeature;
import java.util.List;

/** 충돌·공백 감지 입력. */
public record IssueDetectionContext(
        AiProjectIdentity project,
        AiProjectBrief projectBrief,
        AiSectionContext section,
        List<AiOpinionContext> opinions
) implements AiFeatureContext {

    public IssueDetectionContext {
        opinions = List.copyOf(opinions);
    }

    @Override
    public AiFeature feature() {
        return AiFeature.ISSUE_DETECTION;
    }

    @Override
    public String sourceVersion() {
        return "gate-g" + section.opinionGateGeneration();
    }
}
