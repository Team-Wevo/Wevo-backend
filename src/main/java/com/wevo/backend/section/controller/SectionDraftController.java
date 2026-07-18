package com.wevo.backend.section.controller;

import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.section.dto.request.SectionDraftSaveRequest;
import com.wevo.backend.section.dto.response.SectionDraftSaveResponse;
import com.wevo.backend.section.service.SectionDraftService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 섹션 초안(본문) API. (프로젝트 참여자 전용 — 인증 필요)
 */
@RestController
@RequestMapping("/api/project-sections")
public class SectionDraftController {

    private final SectionDraftService sectionDraftService;

    public SectionDraftController(SectionDraftService sectionDraftService) {
        this.sectionDraftService = sectionDraftService;
    }

    /**
     * 섹션 초안을 저장한다. 새 본문 버전을 발급하고, 기존 검토·외부 링크를 만료 처리한다.
     */
    @PatchMapping("/{projectSectionId}/draft")
    public ResponseEntity<ApiResponse<SectionDraftSaveResponse>> saveDraft(
            @PathVariable Long projectSectionId,
            @Valid @RequestBody SectionDraftSaveRequest request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        SectionDraftSaveResponse response =
                sectionDraftService.saveDraft(projectSectionId, principal.userId(), request);
        return ResponseEntity.ok(
                ApiResponse.success("SECTION_DRAFT_SAVED", "초안이 저장되었습니다.", response));
    }
}
