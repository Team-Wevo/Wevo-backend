package com.wevo.backend.section.controller;

import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.section.dto.response.DraftLeaseAcquireResponse;
import com.wevo.backend.section.service.DraftLeaseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 섹션 초안 편집 잠금 API.
 */
@RestController
@RequestMapping("/api/project-sections")
public class DraftLeaseController {

    private final DraftLeaseService draftLeaseService;

    public DraftLeaseController(DraftLeaseService draftLeaseService) {
        this.draftLeaseService = draftLeaseService;
    }

    @PostMapping("/{projectSectionId}/draft-lease/acquire")
    public ResponseEntity<ApiResponse<DraftLeaseAcquireResponse>> acquire(
            @PathVariable Long projectSectionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        DraftLeaseAcquireResponse response =
                draftLeaseService.acquire(projectSectionId, principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("LEASE_ACQUIRED", "편집권을 획득했습니다.", response));
    }
}
