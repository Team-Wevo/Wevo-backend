package com.wevo.backend.review.dto.response;

import com.wevo.backend.review.domain.TeamReview;
import com.wevo.backend.review.domain.TeamReviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 팀 검토 현황의 팀원 1인 항목.
 *
 * <p>아직 제출하지 않은 팀원은 {@link #pending} 으로 만들어 {@code PENDING} 으로 노출한다.
 * (미제출 상태는 별도 행을 만들지 않는다.)
 *
 * <p>검토자 정보를 엔티티가 아니라 <b>ID·이름 값으로 받는다</b> — 이 항목은 팀 검토 레코드에서
 * 오는 경로와 프로젝트 멤버 로스터에서 오는 경로(미제출 파생) 양쪽에서 만들어지는데,
 * 한쪽 타입에 묶으면 다른 쪽이 타 도메인 엔티티를 끌어와야 한다({@code CLAUDE.md} §6).
 *
 * <p>그래서 {@code reviewId}·{@code changeRequestReason}·{@code reviewedContentVersion}·
 * {@code reviewedAt} 은 필수가 아니다 — 미제출 팀원 항목에서는 비어 있다.
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
@Schema(requiredProperties = {"reviewerUserId", "reviewerName", "status", "resolved", "outdated"})
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

    public static TeamReviewItemResponse of(TeamReview review, Long reviewerUserId, String reviewerName) {
        return new TeamReviewItemResponse(
                review.getId(),
                reviewerUserId,
                reviewerName,
                review.getStatus(),
                review.getChangeRequestReason(),
                review.getReviewedContentVersion(),
                review.isResolved(),
                review.isOutdated(),
                review.getUpdatedAt());
    }

    /** 아직 검토를 제출하지 않은 팀원. */
    public static TeamReviewItemResponse pending(Long reviewerUserId, String reviewerName) {
        return new TeamReviewItemResponse(
                null,
                reviewerUserId,
                reviewerName,
                TeamReviewStatus.PENDING,
                null,
                null,
                false,
                false,
                null);
    }
}
