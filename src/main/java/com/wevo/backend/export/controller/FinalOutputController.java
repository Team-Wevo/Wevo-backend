package com.wevo.backend.export.controller;

import com.wevo.backend.export.dto.response.FinalOutputResponse;
import com.wevo.backend.export.service.FinalOutputService;
import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class FinalOutputController {

    private final FinalOutputService finalOutputService;

    public FinalOutputController(FinalOutputService finalOutputService) {
        this.finalOutputService = finalOutputService;
    }

    /**
     * 최종 결과물 조회 — 전 섹션 확정 시에만 본문을 담고, 그 전에는 진행도만 반환한다.
     * 멤버가 아니면 404(PROJECT_NOT_FOUND/존재 숨김).
     */
    @GetMapping("/{projectId}/final-output")
    public ResponseEntity<ApiResponse<FinalOutputResponse>> getFinalOutput(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long projectId
    ) {
        FinalOutputResponse response = finalOutputService.getFinalOutput(projectId, principal.userId());
        return ResponseEntity.ok(ApiResponse.success("OK", "조회에 성공했습니다.", response));
    }
}
