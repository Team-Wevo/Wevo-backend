package com.wevo.backend.review.controller;

import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.review.dto.request.ExternalReviewSubmitRequest;
import com.wevo.backend.review.dto.response.ExternalReviewViewResponse;
import com.wevo.backend.review.dto.response.ReviewSubmissionResponse;
import com.wevo.backend.review.service.ReviewLinkService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
 *
 * <p>브라우저당 1회 판별을 위해 {@link AnonymousReviewerResolver} 로 익명 검토자 키를 해석·발급한다.
 */
@RestController
@RequestMapping("/public/review-links")
public class PublicReviewController {

    private final ReviewLinkService reviewLinkService;
    private final AnonymousReviewerResolver anonymousReviewerResolver;

    public PublicReviewController(ReviewLinkService reviewLinkService,
                                  AnonymousReviewerResolver anonymousReviewerResolver) {
        this.reviewLinkService = reviewLinkService;
        this.anonymousReviewerResolver = anonymousReviewerResolver;
    }

    /**
     * 토큰으로 섹션 초안 스냅샷을 읽기 전용으로 조회한다.
     *
     * <p>익명 검토자 키를 함께 심어(응답 쿠키), 이 브라우저가 이미 제출했는지를 {@code alreadySubmitted} 로 알려준다.
     */
    @GetMapping("/{token}")
    public ResponseEntity<ApiResponse<ExternalReviewViewResponse>> view(
            @PathVariable String token,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String reviewerId = anonymousReviewerResolver.resolve(request, response);
        ExternalReviewViewResponse body = reviewLinkService.getExternalView(token, reviewerId);
        return ResponseEntity.ok(
                ApiResponse.success("OK", "조회에 성공했습니다.", body));
    }

    /**
     * 이해도를 제출한다. (브라우저당 1회 · 링크당 20개 상한 · 만료/종료 링크 거부)
     */
    @PostMapping("/{token}/submissions")
    public ResponseEntity<ApiResponse<ReviewSubmissionResponse>> submit(
            @PathVariable String token,
            @Valid @RequestBody ExternalReviewSubmitRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String reviewerId = anonymousReviewerResolver.resolve(httpRequest, httpResponse);
        ReviewSubmissionResponse body = reviewLinkService.submitExternalReview(token, reviewerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("REVIEW_SUBMITTED", "이해도가 제출되었습니다.", body));
    }
}
