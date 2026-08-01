package com.wevo.backend.ai.dto.model;

import com.wevo.backend.ai.domain.ProjectFlowFindingType;
import java.util.List;

public record ProjectFlowFindingOutput(
        ProjectFlowFindingType type,
        List<ProjectFlowSectionExcerptOutput> sections,
        String description,
        String suggestion
) {
    public ProjectFlowFindingOutput {
        sections = sections == null ? null : List.copyOf(sections);
    }
}
