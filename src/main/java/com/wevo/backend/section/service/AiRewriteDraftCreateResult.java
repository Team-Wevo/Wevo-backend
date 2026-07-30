package com.wevo.backend.section.service;

import com.wevo.backend.section.domain.ProjectSectionStatus;
import java.util.List;

/** rewrite append 결과. */
public record AiRewriteDraftCreateResult(
        Long draftId,
        int contentVersion,
        ProjectSectionStatus sectionStatus,
        List<DriftedSection> driftedSections
) {

    public AiRewriteDraftCreateResult {
        driftedSections = List.copyOf(driftedSections);
    }
}
