package com.wevo.backend.review.dto.response;

/**
 * 외부 검토자가 링크로 열람하는 섹션 초안(읽기 전용).
 *
 * @param sectionId    섹션 식별자
 * @param sectionTitle 섹션 제목
 * @param content      현재 섹션 초안 본문 (없으면 null)
 */
public record ExternalReviewViewResponse(Long sectionId, String sectionTitle, String content) {
}
