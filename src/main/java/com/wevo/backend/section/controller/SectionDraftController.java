package com.wevo.backend.section.controller;

import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.section.dto.request.SectionDraftSaveRequest;
import com.wevo.backend.section.dto.response.SectionDraftEvidenceResponse;
import com.wevo.backend.section.dto.response.SectionDraftReadResponse;
import com.wevo.backend.section.dto.response.SectionDraftSaveResponse;
import com.wevo.backend.section.service.SectionDraftService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 섹션 초안(본문) API. (프로젝트 참여자 전용)
 */
@RestController
@RequestMapping("/api/project-sections")
public class SectionDraftController {

    private final SectionDraftService sectionDraftService;

    public SectionDraftController(SectionDraftService sectionDraftService) {
        this.sectionDraftService = sectionDraftService;
    }

    /**
     * 섹션의 최신 초안을 조회한다.
     * 초안이 없으면 404(SECTION_DRAFT_NOT_FOUND), 멤버가 아니면 404(SECTION_NOT_FOUND/존재 숨김).
     */
    @GetMapping("/{sectionId}/draft")
    public ResponseEntity<ApiResponse<SectionDraftReadResponse>> getLatestDraft(
            @PathVariable Long sectionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        SectionDraftReadResponse response =
                sectionDraftService.getLatestDraft(sectionId, principal.userId());
        return ResponseEntity.ok(ApiResponse.success("OK", "조회에 성공했습니다.", response));
    }

    /**
     * 섹션 초안을 저장한다.
     * 새 본문 버전을 발급하고, 기존 검토·외부 링크를 만료 처리한다.
     */
    @PutMapping("/{sectionId}/draft")
    public ResponseEntity<ApiResponse<SectionDraftSaveResponse>> saveDraft(
            @PathVariable Long sectionId,
            @Valid @RequestBody SectionDraftSaveRequest request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        SectionDraftSaveResponse response =
                sectionDraftService.saveDraft(sectionId, principal.userId(), request);
        return ResponseEntity.ok(
                ApiResponse.success("DRAFT_SAVED", "초안이 저장되었습니다.", response));
    }

    /**
     * 최신 AI 초안의 근거(사용된 의견·합의점·쟁점 결정·보충 근거)를 조회한다.
     */
    @GetMapping("/{sectionId}/draft/evidence")
    public ResponseEntity<ApiResponse<SectionDraftEvidenceResponse>> getDraftEvidence(
            @PathVariable Long sectionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        SectionDraftEvidenceResponse response =
                sectionDraftService.getDraftEvidence(sectionId, principal.userId());
        return ResponseEntity.ok(ApiResponse.success("OK", "조회에 성공했습니다.", response));
    }
}
