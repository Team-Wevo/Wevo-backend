package com.wevo.backend.review.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wevo.backend.review.domain.ReviewLink;
import com.wevo.backend.review.domain.ReviewLinkStatus;
import java.time.LocalDate;

/**
 * 외부 검토자가 링크로 열람하는 섹션 초안(읽기 전용).
 *
 * <p>본문은 최신 본문이 아니라 <b>링크 발급 시점의 스냅샷</b>이다. 이후 본문이 수정돼도
 * 링크가 가리키던 버전을 그대로 보여준다.
 *
 * @param sectionId        섹션 식별자
 * @param sectionTitle     발급 시점 섹션 제목 스냅샷
 * @param content          발급 시점 본문 스냅샷 (발급 이후 불변 — 초안 없는 발급은 거부되므로 항상 존재)
 * @param contentVersion   링크에 고정된 발급 시점 본문 버전
 * @param linkStatus       링크 상태 (ACTIVE/OUTDATED/EXPIRED/CLOSED — 만료·종료 시 안내 화면 표시).
 *                         유효 기간이 지난 링크는 저장된 상태와 무관하게 {@code EXPIRED} 로 내려간다
 * @param alreadySubmitted 이 브라우저가 이미 제출했는지 (true 면 "이미 검토를 제출했어요." 화면 표시)
 * @param expiresOn        검토를 받는 마지막 날 (KST 날짜). 기간 제한이 없으면 {@code null} 이라
 *                         응답에서 키가 생략된다
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExternalReviewViewResponse(
        Long sectionId,
        String sectionTitle,
        String content,
        Integer contentVersion,
        ReviewLinkStatus linkStatus,
        boolean alreadySubmitted,
        LocalDate expiresOn
) {

    public static ExternalReviewViewResponse of(ReviewLink link, boolean alreadySubmitted,
                                                LocalDate today) {
        return new ExternalReviewViewResponse(
                link.getProjectSection().getId(),
                link.getSectionTitleSnapshot(),
                link.getContentSnapshot(),
                link.getContentVersion(),
                link.statusAsOf(today),
                alreadySubmitted,
                link.getExpiresOn());
    }
}
