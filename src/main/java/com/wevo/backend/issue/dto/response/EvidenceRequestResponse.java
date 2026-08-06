package com.wevo.backend.issue.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
/** 추가 근거 요청 결과. */
@Schema(requiredProperties = {"issueId", "requestedTo"})
public record EvidenceRequestResponse(
        Long issueId,
        RequestedToResponse requestedTo
) {

    public static EvidenceRequestResponse requested(
            Long issueId,
            Long targetUserId,
            String targetUserName
    ) {
        return new EvidenceRequestResponse(
                issueId,
                new RequestedToResponse(targetUserId, targetUserName));
    }

    /** 요청 대상 사용자 스냅샷. */
    @Schema(requiredProperties = {"userId", "name"})
    public record RequestedToResponse(
            Long userId,
            String name
    ) {
    }
}
