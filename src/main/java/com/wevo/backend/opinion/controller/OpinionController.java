package com.wevo.backend.opinion.controller;

import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.opinion.dto.request.OpinionDraftRequest;
import com.wevo.backend.opinion.dto.response.OpinionDraftResponse;
import com.wevo.backend.opinion.service.OpinionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 의견(my-opinion) API. (프로젝트 멤버 전용 — 인증 필요)
 */
@RestController
@RequestMapping("/api/project-sections")
public class OpinionController {

    private final OpinionService opinionService;

    public OpinionController(OpinionService opinionService) {
        this.opinionService = opinionService;
    }

    /**
     * 내 의견을 임시저장한다. 의견이 없으면 새로 만들고, 있으면 덮어쓴다(upsert).
     */
    @PatchMapping("/{projectSectionId}/my-opinion/draft")
    public ResponseEntity<ApiResponse<OpinionDraftResponse>> saveDraft(
            @PathVariable Long projectSectionId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody OpinionDraftRequest request
    ) {
        OpinionDraftResponse response = opinionService.saveDraft(projectSectionId, principal.userId(), request);
        return ResponseEntity.ok(ApiResponse.success("OPINION_DRAFT_SAVED", "의견이 임시저장되었습니다.", response));
    }
}
