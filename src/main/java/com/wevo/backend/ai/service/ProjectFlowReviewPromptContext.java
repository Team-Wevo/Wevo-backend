package com.wevo.backend.ai.service;

import com.wevo.backend.ai.context.AiProjectBrief;
import com.wevo.backend.ai.context.AiProjectIdentity;
import com.wevo.backend.ai.context.ProjectFlowDependencyContext;
import com.wevo.backend.ai.context.ProjectFlowReviewContext;
import com.wevo.backend.ai.context.ProjectFlowReviewSectionContext;
import java.util.List;
import java.util.Set;

/** 전체 단일 호출과 budget 초과 시 모든 section pair 호출이 공유하는 prompt payload. */
public record ProjectFlowReviewPromptContext(
        String mode,
        AiProjectIdentity project,
        AiProjectBrief brief,
        int totalProjectSectionCount,
        List<ProjectFlowReviewSectionContext> sections,
        List<ProjectFlowDependencyContext> dependencies
) {
    public static final String FULL = "FULL";
    public static final String PAIRWISE = "PAIRWISE";

    public ProjectFlowReviewPromptContext {
        sections = List.copyOf(sections);
        dependencies = List.copyOf(dependencies);
    }

    public static ProjectFlowReviewPromptContext full(ProjectFlowReviewContext context) {
        return new ProjectFlowReviewPromptContext(FULL, context.project(), context.brief(),
                context.totalSectionCount(), context.sections(), context.dependencies());
    }

    public static ProjectFlowReviewPromptContext pair(ProjectFlowReviewContext context,
                                                      ProjectFlowReviewSectionContext first,
                                                      ProjectFlowReviewSectionContext second) {
        Set<Long> ids = Set.of(first.sectionId(), second.sectionId());
        List<ProjectFlowDependencyContext> edges = context.dependencies().stream()
                .filter(edge -> ids.contains(edge.fromSectionId()) && ids.contains(edge.toSectionId()))
                .toList();
        return new ProjectFlowReviewPromptContext(PAIRWISE, context.project(), context.brief(),
                context.totalSectionCount(), List.of(first, second), edges);
    }
}
