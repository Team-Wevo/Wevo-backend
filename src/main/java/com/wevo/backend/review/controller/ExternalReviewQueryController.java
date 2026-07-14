package com.wevo.backend.review.controller;

import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.review.dto.response.ExternalReviewResultResponse;
import com.wevo.backend.review.service.ExternalReviewQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 외부 검토 결과 조회 API. (팀장 전용 — 인증 필요)
 *
 * <p>기존 발급 컨트롤러({@code ReviewLinkController})와 분리해 조회 엔드포인트만 담당한다.
 */
@RestController
@RequestMapping("/api/project-sections")
public class ExternalReviewQueryController {

    private final ExternalReviewQueryService externalReviewQueryService;

    public ExternalReviewQueryController(ExternalReviewQueryService externalReviewQueryService) {
        this.externalReviewQueryService = externalReviewQueryService;
    }

    /**
     * 섹션의 외부 검토 결과(이해도 집계 + 개별 코멘트)를 조회한다. (팀장 전용)
     */
    @GetMapping("/{sectionId}/review-submissions")
    public ResponseEntity<ApiResponse<ExternalReviewResultResponse>> getReviewSubmissions(
            @PathVariable Long sectionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        ExternalReviewResultResponse response =
                externalReviewQueryService.getResults(sectionId, principal.userId());
        return ResponseEntity.ok(
                ApiResponse.success("OK", "조회에 성공했습니다.", response));
    }
}
