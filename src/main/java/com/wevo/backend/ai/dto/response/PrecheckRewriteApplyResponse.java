package com.wevo.backend.ai.dto.response;

import com.wevo.backend.ai.service.PrecheckRewriteApplyResult;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.dto.response.DriftedSectionResponse;
import java.util.List;

/** 사전 검토 수정안 적용 후의 섹션 본문 버전과 상태. */
public record PrecheckRewriteApplyResponse(
        int contentVersion,
        ProjectSectionStatus sectionStatus,
        List<DriftedSectionResponse> driftedSections
) {

    public static PrecheckRewriteApplyResponse from(PrecheckRewriteApplyResult result) {
        return new PrecheckRewriteApplyResponse(
                result.contentVersion(),
                result.sectionStatus(),
                result.driftedSections().stream()
                        .map(DriftedSectionResponse::from)
                        .toList()
        );
    }
}
