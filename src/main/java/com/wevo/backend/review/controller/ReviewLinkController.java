package com.wevo.backend.review.controller;

import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.review.dto.request.ReviewLinkStatusUpdateRequest;
import com.wevo.backend.review.dto.response.ReviewLinkResponse;
import com.wevo.backend.review.service.ReviewLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * 외부 검토 링크 발급·종료 API. (팀장 전용)
 *
 * <p>링크 종료의 기본 경로는 서버 자동 처리(본문 수정 시 {@code OUTDATED}, 재발급 시 기존
 * {@code ACTIVE} → {@code CLOSED})이며, 수동 종료는 자동 처리로 덮이지 않는 상황
 * (링크 오발송·유출 등)을 위한 보조 수단이다.
 *
 * <p>조회 엔드포인트는 {@code ExternalReviewQueryController} 가 담당한다.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Reviews", description = "외부 검토 링크·팀 검토 API")
public class ReviewLinkController {

    private final ReviewLinkService reviewLinkService;

    public ReviewLinkController(ReviewLinkService reviewLinkService) {
        this.reviewLinkService = reviewLinkService;
    }

    /**
     * 섹션 외부 검토 링크를 발급한다. (팀장 전용)
     */
    @Operation(summary = "외부 검토 링크 발급 — 섹션당 ACTIVE 1개, 재발급 시 대체 발급 (OWNER 만)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "REVIEW_LINK_CREATED — 원문 토큰은 이 응답으로만 반환"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "A002 — 멤버지만 OWNER 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "S001 — 섹션 없음 또는 비멤버 (존재 숨김)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "R009 — 초안이 없어 발급 불가")
    })
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
     * 외부 검토 링크를 수동으로 종료한다. (팀장 전용)
     *
     * <p>{@code ACTIVE} 링크에서만 성공한다. 이미 종료된 링크는 409({@code R011}), 본문 수정으로
     * 만료된 링크는 409({@code R012})로 거절하며 어느 경우에도 기존 상태는 바뀌지 않는다.
     * 종료된 링크는 다시 살릴 수 없고 새 본문의 외부 검토는 재발급으로만 가능하다.
     */
    @Operation(summary = "외부 검토 링크 비활성화 — CLOSED 로 종료 (OWNER 만)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "REVIEW_LINK_CLOSED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "C001 — CLOSED 외 값 지정"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "A002 — 멤버지만 OWNER 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "R001 — 링크 없음 또는 비멤버 (존재 숨김)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "R011 — 이미 종료된 링크 / R012 — 이미 만료된 링크")
    })
    @PatchMapping("/review-links/{reviewLinkId}")
    public ResponseEntity<ApiResponse<Void>> updateStatus(
            @PathVariable Long reviewLinkId,
            @Valid @RequestBody ReviewLinkStatusUpdateRequest request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        reviewLinkService.updateStatus(reviewLinkId, principal.userId(), request.status());
        return ResponseEntity.ok(
                ApiResponse.success("REVIEW_LINK_CLOSED", "외부 검토 링크가 종료되었습니다.", null));
    }
}
