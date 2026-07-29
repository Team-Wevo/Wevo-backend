package com.wevo.backend.review.controller;

import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.review.dto.response.ReviewLinkResponse;
import com.wevo.backend.review.service.ReviewLinkService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 외부 검토 링크 발급 API. (팀장 전용)
 *
 * <p>링크 종료는 서버 자동 만료(본문 수정 시 {@code OUTDATED}, 재발급 시 기존 {@code ACTIVE}
 * {@code CLOSED})로 처리하므로, 수동 비활성화용 엔드포인트는 두지 않는다.
 * (내부 비활성화 로직은 {@code ReviewLinkService#updateStatus}로 남겨 둔다.)
 *
 * <p>조회 엔드포인트는 {@code ExternalReviewQueryController} 가 담당한다.
 */
@RestController
@RequestMapping("/api")
public class ReviewLinkController {

    private final ReviewLinkService reviewLinkService;

    public ReviewLinkController(ReviewLinkService reviewLinkService) {
        this.reviewLinkService = reviewLinkService;
    }

    /**
     * 섹션 외부 검토 링크를 발급한다. (팀장 전용)
     */
    @PostMapping("/project-sections/{sectionId}/review-links")
    public ResponseEntity<ApiResponse<ReviewLinkResponse>> issueExternalLink(
            @PathVariable Long sectionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        ReviewLinkResponse response = reviewLinkService.issueExternalLink(sectionId, principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("REVIEW_LINK_CREATED", "외부 검토 링크가 발급되었습니다.", response));
    }
}
