package com.wevo.backend.review.dto.response;

import com.wevo.backend.review.domain.TeamReviewStatus;
import java.util.List;

/**
 * 섹션의 팀 검토 현황. (동의 N/M + 팀원별 상태)
 *
 * <p>분모 M({@code totalMembers})은 <b>팀원(MEMBER) 수</b>다. 팀장(OWNER)은 검토자 집계에서 제외된다. (§6.1)
 *
 * @param totalMembers          검토 대상 팀원 수 (동의율 분모 M)
 * @param approvedCount         동의(APPROVED) 수 (동의율 분자 N)
 * @param changesRequestedCount 수정 요청(CHANGES_REQUESTED) 수
 * @param pendingCount          미제출(PENDING) 수
 * @param items                 팀원별 검토 항목
 */
public record TeamReviewStatusResponse(
        long totalMembers,
        long approvedCount,
        long changesRequestedCount,
        long pendingCount,
        List<TeamReviewItemResponse> items
) {

    public static TeamReviewStatusResponse from(List<TeamReviewItemResponse> items) {
        return new TeamReviewStatusResponse(
                items.size(),
                count(items, TeamReviewStatus.APPROVED),
                count(items, TeamReviewStatus.CHANGES_REQUESTED),
                count(items, TeamReviewStatus.PENDING),
                items);
    }

    private static long count(List<TeamReviewItemResponse> items, TeamReviewStatus status) {
        return items.stream().filter(item -> item.status() == status).count();
    }
}
