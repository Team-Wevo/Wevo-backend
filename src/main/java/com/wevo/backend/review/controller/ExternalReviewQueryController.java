package com.wevo.backend.review.controller;

import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.review.dto.response.ExternalReviewResultResponse;
import com.wevo.backend.review.dto.response.ReviewLinkCurrentResponse;
import com.wevo.backend.review.service.ExternalReviewQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 외부 검토 조회 API. (팀장 전용 — 인증 필요)
 *
 * <p>발급 컨트롤러({@code ReviewLinkController})와 분리해 조회 엔드포인트만 담당한다.
 * 검토 결과 조회와 현재 활성 링크 상태 복구 조회가 여기에 모인다.
 */
@RestController
@RequestMapping("/api/project-sections")
@Tag(name = "Reviews", description = "외부 검토 링크·팀 검토 API")
public class ExternalReviewQueryController {

    private final ExternalReviewQueryService externalReviewQueryService;

    public ExternalReviewQueryController(ExternalReviewQueryService externalReviewQueryService) {
        this.externalReviewQueryService = externalReviewQueryService;
    }

    /**
     * 섹션의 외부 검토 결과(이해도 집계 + 개별 코멘트)를 조회한다. (팀장 전용)
     */
    @Operation(summary = "외부 검토 결과 조회 — 이해도 집계·버전별 집계·개별 목록 (OWNER 만)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "OK"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "A002 — 멤버지만 OWNER 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "S001 — 섹션 없음 또는 비멤버 (존재 숨김)")
    })
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

    /**
     * 섹션의 현재 활성 외부 검토 링크를 조회한다. (팀장 전용)
     *
     * <p>발급 후 새로고침해도 활성 링크 존재 여부를 알 수 있게 해, 불필요한 재발급으로 이미 공유한
     * 링크가 죽는 사고를 막는다. 토큰은 노출하지 않는다.
     *
     * <p>활성 링크가 없어도 조회 자체는 성공이므로 {@code 200 OK} 로 응답하고 {@code data} 만
     * 생략한다({@code null} 필드는 직렬화 제외).
     */
    @Operation(summary = "현재 활성 검토 링크 조회 — 재발급 사고 방지용 상태 복구 (토큰 미포함)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "OK — 활성 링크가 없으면 data 가 null 로 생략된다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "A002 — 멤버지만 OWNER 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "S001 — 섹션 없음 또는 비멤버 (존재 숨김)")
    })
    @GetMapping("/{sectionId}/review-links/current")
    public ResponseEntity<ApiResponse<ReviewLinkCurrentResponse>> getCurrentLink(
            @PathVariable Long sectionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return externalReviewQueryService.getCurrentActiveLink(sectionId, principal.userId())
                .map(current -> ResponseEntity.ok(
                        ApiResponse.success("OK", "현재 활성 외부 검토 링크를 조회했습니다.", current)))
                .orElseGet(() -> ResponseEntity.ok(
                        ApiResponse.success("OK", "활성 외부 검토 링크가 없습니다.", null)));
    }
}
