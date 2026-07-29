package com.wevo.backend.ai.service;

import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.service.DriftedSection;
import java.util.List;

/** 사전 검토 rewrite 적용 결과. AI-11 전까지 drift 목록은 항상 비어 있다. */
public record PrecheckRewriteApplyResult(
        int contentVersion,
        ProjectSectionStatus sectionStatus,
        List<DriftedSection> driftedSections
) {

    public PrecheckRewriteApplyResult {
        driftedSections = List.copyOf(driftedSections);
    }
}
