package com.wevo.backend.section.controller;

import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.section.dto.response.SectionConfirmReadinessResponse;
import com.wevo.backend.section.service.SectionConfirmReadinessService;
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
 * 섹션 확정 가능 여부 조회 API. (API_SPEC §3.7.5, 프로젝트 참여자 전용)
 */
@RestController
@RequestMapping("/api/project-sections")
@Tag(name = "Sections", description = "섹션 초안·편집 잠금·검토 요청·확정 API")
public class SectionConfirmReadinessController {

    private final SectionConfirmReadinessService sectionConfirmReadinessService;

    public SectionConfirmReadinessController(SectionConfirmReadinessService sectionConfirmReadinessService) {
        this.sectionConfirmReadinessService = sectionConfirmReadinessService;
    }

    /**
     * 섹션 확정 조건별 충족 여부와 확정 가능 여부를 조회한다.
     * 섹션이 없거나 멤버가 아니면 404(SECTION_NOT_FOUND/존재 숨김).
     */
    @Operation(summary = "확정 가능 여부 조회 — 조건별 충족 여부·비활성 사유 (ready와 canConfirm 분리)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "OK"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "S001 — 섹션 없음 또는 비멤버 (존재 숨김)")
    })
    @GetMapping("/{sectionId}/confirm-readiness")
    public ResponseEntity<ApiResponse<SectionConfirmReadinessResponse>> getReadiness(
            @PathVariable Long sectionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        SectionConfirmReadinessResponse response =
                sectionConfirmReadinessService.getReadiness(sectionId, principal.userId());
        return ResponseEntity.ok(ApiResponse.success("OK", "조회에 성공했습니다.", response));
    }
}
