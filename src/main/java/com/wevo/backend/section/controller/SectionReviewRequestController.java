package com.wevo.backend.section.controller;

import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.section.dto.response.SectionReviewRequestResponse;
import com.wevo.backend.section.service.SectionReviewRequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 섹션 검토 요청 API. (프로젝트 참여자 전용)
 */
@RestController
@RequestMapping("/api/project-sections")
public class SectionReviewRequestController {

    private final SectionReviewRequestService sectionReviewRequestService;

    public SectionReviewRequestController(SectionReviewRequestService sectionReviewRequestService) {
        this.sectionReviewRequestService = sectionReviewRequestService;
    }

    /**
     * 초안 작성이 끝난 섹션을 검토 단계로 보낸다. (DRAFTING → REVIEWING)
     */
    @PostMapping("/{sectionId}/review-request")
    public ResponseEntity<ApiResponse<SectionReviewRequestResponse>> requestReview(
            @PathVariable Long sectionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        SectionReviewRequestResponse response =
                sectionReviewRequestService.request(sectionId, principal.userId());
        return ResponseEntity.ok(
                ApiResponse.success("REVIEW_REQUESTED", "검토가 요청되었습니다.", response));
    }
}
