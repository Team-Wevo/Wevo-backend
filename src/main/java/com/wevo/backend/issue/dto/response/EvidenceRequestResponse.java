package com.wevo.backend.issue.dto.response;

/** 추가 근거 요청 결과. */
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
    public record RequestedToResponse(
            Long userId,
            String name
    ) {
    }
}
