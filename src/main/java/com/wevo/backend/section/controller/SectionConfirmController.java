package com.wevo.backend.section.controller;

import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.section.dto.response.SectionConfirmResponse;
import com.wevo.backend.section.service.SectionConfirmService;
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
 * 섹션 확정 API. (팀장 전용)
 */
@RestController
@RequestMapping("/api/project-sections")
@Tag(name = "Sections", description = "섹션 초안·편집 잠금·검토 요청·확정 API")
public class SectionConfirmController {

    private final SectionConfirmService sectionConfirmService;

    public SectionConfirmController(SectionConfirmService sectionConfirmService) {
        this.sectionConfirmService = sectionConfirmService;
    }

    /**
     * 검토가 끝난 섹션을 확정한다. (REVIEWING → CONFIRMED)
     * OWNER가 아니면 403(A002), 섹션이 REVIEWING이 아니면 409(S002),
     * §6.3 조건 미충족이면 409(C003, 조건별 사유 포함).
     */
    @Operation(summary = "섹션 확정 — 조건 재검증 후 REVIEWING → CONFIRMED (OWNER 만)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "SECTION_CONFIRMED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "A002 — 멤버지만 OWNER 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "S001 — 섹션 없음 또는 비멤버 (존재 숨김)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "S002 — REVIEWING 아님 / C003 — 확정 조건 미충족 (errors[]에 조건별 사유)")
    })
    @PostMapping("/{sectionId}/confirm")
    public ResponseEntity<ApiResponse<SectionConfirmResponse>> confirm(
            @PathVariable Long sectionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        SectionConfirmResponse response =
                sectionConfirmService.confirm(sectionId, principal.userId());
        return ResponseEntity.ok(
                ApiResponse.success("SECTION_CONFIRMED", "섹션이 확정되었습니다.", response));
    }
}
