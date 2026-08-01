package com.wevo.backend.ai.dto.model;

import java.util.List;

public record ProjectFlowReviewOutput(List<ProjectFlowFindingOutput> findings) {
    public ProjectFlowReviewOutput {
        findings = findings == null ? null : List.copyOf(findings);
    }
}
