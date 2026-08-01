package com.wevo.backend.review.controller;

import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.review.dto.request.TeamReviewResolveRequest;
import com.wevo.backend.review.dto.request.TeamReviewSubmitRequest;
import com.wevo.backend.review.dto.response.TeamReviewItemResponse;
import com.wevo.backend.review.dto.response.TeamReviewStatusResponse;
import com.wevo.backend.review.service.TeamReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@Tag(name = "Reviews", description = "외부 검토 링크·팀 검토 API")
public class TeamReviewController {

    private final TeamReviewService teamReviewService;

    public TeamReviewController(TeamReviewService teamReviewService) {
        this.teamReviewService = teamReviewService;
    }

    /**
     * 섹션의 팀 검토 현황(동의 N/M + 팀원별 상태)을 조회한다. (역할 무관)
     */
    @Operation(summary = "팀 검토 목록 조회 — 상태별 집계와 멤버별 검토 (미제출 멤버는 PENDING 파생)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "OK"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "S001 — 섹션 없음 또는 비멤버 (존재 숨김)")
    })
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
    @Operation(summary = "내 팀 검토 제출 — contentVersion 바인딩, MEMBER 전용 (upsert 멱등)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "TEAM_REVIEW_SUBMITTED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "C001 — status 누락 / 수정요청인데 사유 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "A002 — OWNER 는 검토자가 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "S001 — 섹션 없음 또는 비멤버 (존재 숨김)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "R006 — REVIEWING 아님 / C003 — contentVersion 불일치")
    })
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

    /**
     * 수정 요청을 해소(resolved) 처리한다. (팀장 전용 — 수정 안 하고 합의된 경우)
     */
    @Operation(summary = "수정 요청 처리(resolve) — 미해결 수정요청 수만 줄이고 status 는 유지 (OWNER 만)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "TEAM_REVIEW_RESOLVED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "C001 — resolved 누락"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "A002 — 멤버지만 OWNER 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "R007 — 팀 검토 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "R008 — CHANGES_REQUESTED 상태가 아님")
    })
    @PatchMapping("/{sectionId}/team-reviews/{reviewId}")
    public ResponseEntity<ApiResponse<TeamReviewItemResponse>> resolveChangeRequest(
            @PathVariable Long sectionId,
            @PathVariable Long reviewId,
            @Valid @RequestBody TeamReviewResolveRequest request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        TeamReviewItemResponse response = teamReviewService.resolveChangeRequest(
                sectionId, reviewId, principal.userId(), request.resolved());
        return ResponseEntity.ok(
                ApiResponse.success("TEAM_REVIEW_RESOLVED", "수정 요청이 처리되었습니다.", response));
    }
}
