package com.wevo.backend.review.dto.response;

import com.wevo.backend.review.domain.ReviewLink;
import com.wevo.backend.review.domain.ReviewLinkStatus;

/**
 * 외부 검토자가 링크로 열람하는 섹션 초안(읽기 전용).
 *
 * <p>본문은 최신 본문이 아니라 <b>링크 발급 시점의 스냅샷</b>이다. 이후 본문이 수정돼도
 * 링크가 가리키던 버전을 그대로 보여준다.
 *
 * @param sectionId        섹션 식별자
 * @param sectionTitle     발급 시점 섹션 제목 스냅샷
 * @param content          발급 시점 본문 스냅샷 (없으면 null)
 * @param contentVersion   스냅샷 본문 버전
 * @param linkStatus       링크 상태 (ACTIVE/OUTDATED/CLOSED — 만료·종료 시 안내 화면 표시)
 * @param alreadySubmitted 이 브라우저가 이미 제출했는지 (true 면 "이미 검토를 제출했어요." 화면 표시)
 */
public record ExternalReviewViewResponse(
        Long sectionId,
        String sectionTitle,
        String content,
        Integer contentVersion,
        ReviewLinkStatus linkStatus,
        boolean alreadySubmitted
) {

    public static ExternalReviewViewResponse of(ReviewLink link, boolean alreadySubmitted) {
        return new ExternalReviewViewResponse(
                link.getProjectSection().getId(),
                link.getSectionTitleSnapshot(),
                link.getContentSnapshot(),
                link.getContentVersion(),
                link.getStatus(),
                alreadySubmitted);
    }
}
