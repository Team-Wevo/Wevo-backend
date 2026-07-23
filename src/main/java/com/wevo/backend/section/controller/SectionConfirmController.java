package com.wevo.backend.section.controller;

import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.section.dto.response.SectionConfirmResponse;
import com.wevo.backend.section.service.SectionConfirmService;
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
