package com.wevo.backend.review.controller;

import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.review.dto.request.ExternalReviewSubmitRequest;
import com.wevo.backend.review.dto.response.ExternalReviewViewResponse;
import com.wevo.backend.review.dto.response.ReviewSubmissionResponse;
import com.wevo.backend.review.service.ReviewLinkService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 외부 검토 API.
 *
 * <p>{@code /public} 경로는 SecurityConfig 에서 인증 예외로 허용.
 * 외부 검토 결과는 섹션 확정 조건에 포함되지 않는다.
 */
@RestController
@RequestMapping("/public/review-links")
public class PublicReviewController {

    private final ReviewLinkService reviewLinkService;

    public PublicReviewController(ReviewLinkService reviewLinkService) {
        this.reviewLinkService = reviewLinkService;
    }

    /**
     * 토큰으로 섹션 초안을 읽기 전용으로 조회한다.
     */
    @GetMapping("/{token}")
    public ResponseEntity<ApiResponse<ExternalReviewViewResponse>> view(
            @PathVariable String token
    ) {
        ExternalReviewViewResponse response = reviewLinkService.getExternalView(token);
        return ResponseEntity.ok(
                ApiResponse.success("OK", "조회에 성공했습니다.", response));
    }

    /**
     * 이해도를 제출한다.
     */
    @PostMapping("/{token}/submissions")
    public ResponseEntity<ApiResponse<ReviewSubmissionResponse>> submit(
            @PathVariable String token,
            @Valid @RequestBody ExternalReviewSubmitRequest request
    ) {
        ReviewSubmissionResponse response = reviewLinkService.submitExternalReview(token, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("REVIEW_SUBMITTED", "이해도가 제출되었습니다.", response));
    }
}
