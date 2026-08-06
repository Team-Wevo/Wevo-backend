package com.wevo.backend.section.controller;

import com.wevo.backend.global.config.ApiExampleRefs;
import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.section.dto.request.SectionDraftSaveRequest;
import com.wevo.backend.section.dto.response.SectionDraftEvidenceResponse;
import com.wevo.backend.section.dto.response.SectionDraftReadResponse;
import com.wevo.backend.section.dto.response.SectionDraftSaveResponse;
import com.wevo.backend.section.service.SectionDraftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Sections", description = "섹션 초안·편집 잠금·검토 요청·확정 API")
public class SectionDraftController {

    private final SectionDraftService sectionDraftService;

    public SectionDraftController(SectionDraftService sectionDraftService) {
        this.sectionDraftService = sectionDraftService;
    }

    /**
     * 섹션의 최신 초안을 조회한다.
     * 초안이 없으면 404(SECTION_DRAFT_NOT_FOUND), 멤버가 아니면 404(SECTION_NOT_FOUND/존재 숨김).
     */
    @Operation(summary = "최신 초안 조회 — 본문·버전·현재 편집자")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "OK"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "S001 — 섹션 없음 또는 비멤버 (존재 숨김) / S003 — 초안 없음")
    })
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
    @Operation(summary = "초안 저장 — 편집권 보유자만, 버전 +1. 동일 본문이면 멱등")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "DRAFT_SAVED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "C001 — content·baseVersion 검증 실패 (최대 10,000자)",
                    content = @Content(examples = @ExampleObject(
                            name = "C001", ref = ApiExampleRefs.INVALID_INPUT))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요",
                    content = @Content(examples = @ExampleObject(
                            name = "A001", ref = ApiExampleRefs.UNAUTHORIZED))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "S001 — 섹션 없음 또는 비멤버 (존재 숨김)",
                    content = @Content(examples = @ExampleObject(
                            name = "S001", ref = ApiExampleRefs.SECTION_NOT_FOUND))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "C003 — baseVersion 충돌 / S002 — 초안 없는 단계 / S004 — 타인 편집 중 / S005 — 편집권 미보유",
                    content = @Content(examples = @ExampleObject(
                            name = "C003", ref = ApiExampleRefs.CONFLICT)))
    })
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
    @Operation(summary = "초안 근거 보기 — 생성에 사용된 의견·합의점·결정·보충 근거")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "OK"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "S001 — 섹션 없음 또는 비멤버 (존재 숨김) / S003 — 초안 없음")
    })
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
