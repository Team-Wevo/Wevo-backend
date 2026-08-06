package com.wevo.backend.review.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wevo.backend.review.domain.ReviewLink;
import com.wevo.backend.review.domain.ReviewLinkStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 현재 섹션에 살아있는(ACTIVE) 외부 검토 링크의 상태 복구용 조회 결과.
 *
 * <p>팀장이 링크 발급 후 새로고침하면 활성 링크 존재 여부를 알 수 없어, 무심코 재발급 →
 * 이미 공유한 링크가 죽는 사고가 난다. 이 응답으로 현재 {@code ACTIVE} 링크의 존재·메타데이터를
 * 복구해, 프론트가 재발급 대신 기존 링크를 계속 쓰도록 안내할 수 있게 한다.
 *
 * <p><b>토큰은 포함하지 않는다.</b> 원문 토큰은 발급({@code REVIEW_LINK_CREATED}) 응답으로만
 * 한 번 반환되고 DB 에는 해시만 저장되므로, 복구 조회로는 재노출하지 않는다.
 *
 * @param reviewLinkId    활성 링크 ID
 * @param linkStatus      링크 상태 (활성 링크만 반환하므로 항상 {@code ACTIVE} — 계약 명시용).
 *                        공개 열람 응답({@code ExternalReviewViewResponse})과 같은 개념이므로
 *                        이름을 맞춘다.
 * @param contentVersion  링크가 고정한 발급 시점 본문 버전
 * @param submissionCount 이 <b>링크에</b> 쌓인 외부 검토 제출 수 (링크당 20개 상한)
 * @param issuedAt        링크 발급 시각
 * @param expiresOn       링크가 살아 있는 마지막 날 (KST 날짜). 기간을 지정하지 않았으면 {@code null}
 *                        이라 응답에서 키가 생략된다 — FE 는 키 유무로 "기간 제한 없음"을 판단한다
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReviewLinkCurrentResponse(
        Long reviewLinkId,
        ReviewLinkStatus linkStatus,
        Integer contentVersion,
        long submissionCount,
        LocalDateTime issuedAt,
        LocalDate expiresOn
) {

    public static ReviewLinkCurrentResponse of(ReviewLink link, long submissionCount) {
        return new ReviewLinkCurrentResponse(
                link.getId(),
                link.getStatus(),
                link.getContentVersion(),
                submissionCount,
                link.getCreatedAt(),
                link.getExpiresOn());
    }
}
