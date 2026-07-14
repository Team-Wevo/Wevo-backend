package com.wevo.backend.review.controller;

import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.review.dto.request.ReviewLinkStatusUpdateRequest;
import com.wevo.backend.review.dto.response.ReviewLinkResponse;
import com.wevo.backend.review.service.ReviewLinkService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 외부 검토 링크 발급·상태변경 API. (팀장 전용)
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

    /**
     * 외부 검토 링크 상태를 변경한다. (팀장 전용)
     */
    @PatchMapping("/review-links/{reviewLinkId}")
    public ResponseEntity<ApiResponse<Void>> updateStatus(
            @PathVariable Long reviewLinkId,
            @Valid @RequestBody ReviewLinkStatusUpdateRequest request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        reviewLinkService.updateStatus(reviewLinkId, principal.userId(), request.status());
        return ResponseEntity.ok(
                ApiResponse.success("REVIEW_LINK_CLOSED", "외부 검토 링크가 비활성화되었습니다.", null));
    }
}
