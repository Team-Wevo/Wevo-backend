package com.wevo.backend.review.dto.response;

import com.wevo.backend.review.domain.ReviewLink;

/**
 * 외부 검토 링크 발급 결과. (팀장이 받아 공유할 토큰)
 *
 * @param reviewLinkId 발급된 링크 식별자
 * @param token        공개 접근용 토큰 (/public/review-links/{token})
 */
public record ReviewLinkResponse(Long reviewLinkId, String token) {

    public static ReviewLinkResponse from(ReviewLink link) {
        return new ReviewLinkResponse(link.getId(), link.getToken());
    }
}
