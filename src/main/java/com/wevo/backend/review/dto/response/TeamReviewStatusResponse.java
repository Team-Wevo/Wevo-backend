package com.wevo.backend.review.dto.response;

import com.wevo.backend.review.domain.TeamReviewStatus;
import java.util.List;

/**
 * 섹션의 팀 검토 현황. (동의 N/M + 팀원별 상태)
 *
 * <p>분모 M({@code totalMembers})은 <b>팀원(MEMBER) 수</b>다. 팀장(OWNER)은 검토자 집계에서 제외된다. (§6.1)
 *
 * <p>본문이 수정되면 기존 검토는 OUTDATED가 되어 <b>현재 유효한 동의로 세지 않는다.</b>
 * 그래서 {@code approvedCount(N)}·{@code changesRequestedCount}는 <b>outdated 아닌</b> 것만 세고,
 * outdated는 {@code outdatedCount}로 따로 노출한다. 네 값은 {@code totalMembers}를 분할한다.
 * {@code unresolvedChangesRequestedCount}는 확정 조건 판정용 <b>부분 집계</b>로 분할에 포함되지 않는다.
 *
 * @param currentContentVersion           서버의 현재 본문 버전 — 검토 제출(§3.5.7)의
 *                                        {@code contentVersion}에 그대로 사용. 초안이 없으면 null(생략)
 * @param totalMembers                    검토 대상 팀원 수 (동의율 분모 M)
 * @param approvedCount                   현재 유효 동의 수 (APPROVED 이고 outdated 아님 — 동의율 분자 N)
 * @param changesRequestedCount           현재 유효 수정 요청 수 (CHANGES_REQUESTED 이고 outdated 아님 —
 *                                        해소(resolved) 여부 무관)
 * @param pendingCount                    미제출(PENDING) 수
 * @param outdatedCount                   본문 수정으로 만료된 검토 수
 * @param unresolvedChangesRequestedCount 유효 수정 요청 중 미해결(resolved=false)만 —
 *                                        확정 조건 {@code NO_UNRESOLVED_REQUEST}와 같은 기준 (§6.3)
 * @param items                           팀원별 검토 항목
 */
public record TeamReviewStatusResponse(
        Integer currentContentVersion,
        long totalMembers,
        long approvedCount,
        long changesRequestedCount,
        long pendingCount,
        long outdatedCount,
        long unresolvedChangesRequestedCount,
        List<TeamReviewItemResponse> items
) {

    public static TeamReviewStatusResponse from(List<TeamReviewItemResponse> items, Integer currentContentVersion) {
        return new TeamReviewStatusResponse(
                currentContentVersion,
                items.size(),
                countActive(items, TeamReviewStatus.APPROVED),
                countActive(items, TeamReviewStatus.CHANGES_REQUESTED),
                count(items, TeamReviewStatus.PENDING),
                items.stream().filter(TeamReviewItemResponse::outdated).count(),
                items.stream()
                        .filter(item -> !item.outdated()
                                && item.status() == TeamReviewStatus.CHANGES_REQUESTED
                                && !item.resolved())
                        .count(),
                items);
    }

    /** outdated 아닌(=현재 유효한) 특정 상태 수. */
    private static long countActive(List<TeamReviewItemResponse> items, TeamReviewStatus status) {
        return items.stream()
                .filter(item -> !item.outdated() && item.status() == status)
                .count();
    }

    private static long count(List<TeamReviewItemResponse> items, TeamReviewStatus status) {
        return items.stream().filter(item -> item.status() == status).count();
    }
}
