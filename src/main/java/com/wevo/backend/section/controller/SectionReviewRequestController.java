package com.wevo.backend.section.controller;

import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.section.dto.response.SectionReviewRequestResponse;
import com.wevo.backend.section.service.SectionReviewRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Sections", description = "섹션 초안·편집 잠금·검토 요청·확정 API")
public class SectionReviewRequestController {

    private final SectionReviewRequestService sectionReviewRequestService;

    public SectionReviewRequestController(SectionReviewRequestService sectionReviewRequestService) {
        this.sectionReviewRequestService = sectionReviewRequestService;
    }

    /**
     * 초안 작성이 끝난 섹션을 검토 단계로 보낸다. (DRAFTING → REVIEWING)
     */
    @Operation(summary = "검토 요청 — DRAFTING → REVIEWING 전이, 팀 검토 PENDING 초기화")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "REVIEW_REQUESTED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "S001 — 섹션 없음 또는 비멤버 (존재 숨김) / S003 — 초안 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "S002 — DRAFTING 아님 / S006 — AI 사전 검토 미완료 / S004 — 타인이 편집 중")
    })
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
