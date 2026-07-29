package com.wevo.backend.section.service;

import com.wevo.backend.section.domain.ProjectSectionStatus;

/** rewrite append 결과. */
public record AiRewriteDraftCreateResult(
        Long draftId,
        int contentVersion,
        ProjectSectionStatus sectionStatus
) {
}
