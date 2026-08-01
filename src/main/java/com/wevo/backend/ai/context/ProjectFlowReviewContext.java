package com.wevo.backend.ai.context;

import com.wevo.backend.ai.domain.AiFeature;
import java.util.List;

/** 프로젝트 전체 확정본의 흐름·일관성 점검용 provider 중립 입력. */
public record ProjectFlowReviewContext(
        AiProjectIdentity project,
        AiProjectBrief brief,
        int totalSectionCount,
        int confirmedSectionCount,
        List<ProjectFlowReviewSectionContext> sections,
        List<ProjectFlowDependencyContext> dependencies
) implements AiFeatureContext {
    public ProjectFlowReviewContext {
        if (project == null || brief == null || totalSectionCount <= 0
                || confirmedSectionCount < 0 || confirmedSectionCount > totalSectionCount
                || sections == null || dependencies == null) {
            throw new IllegalArgumentException("project flow review context가 유효하지 않습니다.");
        }
        sections = List.copyOf(sections);
        dependencies = List.copyOf(dependencies);
    }

    @Override
    public AiFeature feature() {
        return AiFeature.PROJECT_FLOW_REVIEW;
    }

    @Override
    public String sourceVersion() {
        return "project-flow-review-source:v1";
    }
}
