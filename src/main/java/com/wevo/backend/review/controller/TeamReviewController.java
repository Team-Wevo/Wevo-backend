package com.wevo.backend.review.controller;

import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.review.dto.request.TeamReviewSubmitRequest;
import com.wevo.backend.review.dto.response.TeamReviewItemResponse;
import com.wevo.backend.review.dto.response.TeamReviewStatusResponse;
import com.wevo.backend.review.service.TeamReviewService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 팀(내부) 검토 API. (인증 필요 — 제품 정책서 §6.1)
 *
 */
@RestController
@RequestMapping("/api/project-sections")
public class TeamReviewController {

    private final TeamReviewService teamReviewService;

    public TeamReviewController(TeamReviewService teamReviewService) {
        this.teamReviewService = teamReviewService;
    }

    /**
     * 섹션의 팀 검토 현황(동의 N/M + 팀원별 상태)을 조회한다. (역할 무관)
     */
    @GetMapping("/{sectionId}/team-reviews")
    public ResponseEntity<ApiResponse<TeamReviewStatusResponse>> getTeamReviews(
            @PathVariable Long sectionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        TeamReviewStatusResponse response = teamReviewService.getStatus(sectionId, principal.userId());
        return ResponseEntity.ok(
                ApiResponse.success("OK", "조회에 성공했습니다.", response));
    }

    /**
     * 내 팀 검토를 제출한다. (팀원 전용 — 동의/수정요청)
     */
    @PutMapping("/{sectionId}/team-reviews/me")
    public ResponseEntity<ApiResponse<TeamReviewItemResponse>> submitMyReview(
            @PathVariable Long sectionId,
            @Valid @RequestBody TeamReviewSubmitRequest request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        TeamReviewItemResponse response =
                teamReviewService.submitMyReview(sectionId, principal.userId(), request);
        return ResponseEntity.ok(
                ApiResponse.success("TEAM_REVIEW_SUBMITTED", "검토가 제출되었습니다.", response));
    }
}
