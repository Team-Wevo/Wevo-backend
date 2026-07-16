package com.wevo.backend.review.dto.response;

import com.wevo.backend.review.domain.TeamReview;
import com.wevo.backend.review.domain.TeamReviewStatus;
import com.wevo.backend.user.domain.User;
import java.time.LocalDateTime;

/**
 * 팀 검토 현황의 팀원 1인 항목.
 *
 * <p>아직 제출하지 않은 팀원은 {@link #pending(User)} 로 만들어 {@code PENDING} 으로 노출한다.
 * (미제출 상태는 별도 행을 만들지 않는다.)
 *
 * @param reviewId              검토 식별자 (미제출이면 null)
 * @param reviewerUserId        검토자(팀원) ID
 * @param reviewerName          검토자 표시 이름
 * @param status                동의/수정요청/대기
 * @param changeRequestReason   수정 요청 사유
 * @param reviewedContentVersion 검토 시점 본문 버전
 * @param resolved              수정 요청 해결 여부
 * @param outdated              본문 수정으로 만료됐는지 (이전 본문 기준 검토)
 * @param reviewedAt            제출·갱신 시각
 */
public record TeamReviewItemResponse(
        Long reviewId,
        Long reviewerUserId,
        String reviewerName,
        TeamReviewStatus status,
        String changeRequestReason,
        Integer reviewedContentVersion,
        boolean resolved,
        boolean outdated,
        LocalDateTime reviewedAt
) {

    public static TeamReviewItemResponse of(TeamReview review, User reviewer) {
        return new TeamReviewItemResponse(
                review.getId(),
                reviewer.getId(),
                reviewer.getName(),
                review.getStatus(),
                review.getChangeRequestReason(),
                review.getReviewedContentVersion(),
                review.isResolved(),
                review.isOutdated(),
                review.getUpdatedAt());
    }

    /** 아직 검토를 제출하지 않은 팀원. */
    public static TeamReviewItemResponse pending(User reviewer) {
        return new TeamReviewItemResponse(
                null,
                reviewer.getId(),
                reviewer.getName(),
                TeamReviewStatus.PENDING,
                null,
                null,
                false,
                false,
                null);
    }
}
