package com.wevo.backend.issue.dto.response;

import com.wevo.backend.issue.domain.IssueStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** CONFLICT 쟁점 결정 응답. (API_SPEC §3.9.1) */
@Schema(requiredProperties = {"issueId", "status"})
public record IssueDecisionResponse(
        Long issueId,
        IssueStatus status
) {

    public static IssueDecisionResponse resolved(Long issueId) {
        return new IssueDecisionResponse(issueId, IssueStatus.RESOLVED);
    }
}
