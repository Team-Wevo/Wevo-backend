package com.wevo.backend.review.dto.response;

import com.wevo.backend.review.domain.ReviewLink;

/**
 * 외부 검토 링크 발급 결과. (팀장이 받아 공유할 토큰)
 *
 * <p>원문 토큰은 DB 에 저장되지 않고(해시만 저장) 발급 응답으로만 한 번 전달되므로,
 * 엔티티가 아니라 발급 시점의 원문 토큰을 직접 받아 구성한다.
 *
 * @param reviewLinkId 발급된 링크 식별자
 * @param token        공개 접근용 원문 토큰 (/public/review-links/{token})
 */
public record ReviewLinkResponse(Long reviewLinkId, String token) {

    public static ReviewLinkResponse of(ReviewLink link, String rawToken) {
        return new ReviewLinkResponse(link.getId(), rawToken);
    }
}
