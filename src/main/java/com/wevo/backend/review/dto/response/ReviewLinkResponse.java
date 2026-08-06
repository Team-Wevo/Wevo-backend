package com.wevo.backend.review.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wevo.backend.review.domain.ReviewLink;
import java.time.LocalDate;

/**
 * 외부 검토 링크 발급 결과. (팀장이 받아 공유할 토큰)
 *
 * <p>원문 토큰은 DB 에 저장되지 않고(해시만 저장) 발급 응답으로만 한 번 전달되므로,
 * 엔티티가 아니라 발급 시점의 원문 토큰을 직접 받아 구성한다.
 *
 * @param reviewLinkId   발급된 링크 식별자
 * @param token          공개 접근용 원문 토큰 (/public/review-links/{token})
 * @param contentVersion 링크에 고정된 발급 시점 본문 버전 (발급 이후 불변)
 * @param expiresOn      링크가 살아 있는 마지막 날 (KST 날짜). 기간을 지정하지 않았으면 {@code null}
 *                       이라 응답에서 키가 생략된다 (CLAUDE.md §5.4)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReviewLinkResponse(Long reviewLinkId, String token, Integer contentVersion,
                                 LocalDate expiresOn) {

    public static ReviewLinkResponse of(ReviewLink link, String rawToken) {
        return new ReviewLinkResponse(
                link.getId(), rawToken, link.getContentVersion(), link.getExpiresOn());
    }
}
