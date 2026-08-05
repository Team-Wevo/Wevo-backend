package com.wevo.backend.ai.controller;

import com.wevo.backend.ai.dto.response.AiJobAcceptedResponse;
import com.wevo.backend.ai.service.DraftGenerationRequestService;
import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 현재 정리 세트를 바탕으로 섹션 초안을 생성하는 비동기 API. */
@RestController
@RequestMapping("/api/project-sections")
@Tag(name = "AI Facilitation", description = "AI 정리·초안·사전 검토 API")
public class DraftGenerationController {

    private final DraftGenerationRequestService requestService;

    public DraftGenerationController(DraftGenerationRequestService requestService) {
        this.requestService = requestService;
    }

    @Operation(
            summary = "결정 반영해 초안 만들기",
            description = """
                    OWNER가 비동기 초안 생성을 요청합니다. 동일 snapshot의 진행 중·성공 작업도
                    202와 같은 requestId를 반환하며 GET /api/ai-jobs/{requestId}로 polling합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "202", description = "DRAFT_GENERATION_REQUESTED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "A002 — OWNER 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "S001 — 섹션 없음 또는 비멤버"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "S002, I002, AI030 또는 C003 — 상태·선행 결과·쟁점·snapshot 충돌"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503", description = "AI008 — AI Provider 비활성·연결 불가")
    })
    @PostMapping("/{sectionId}/draft/generate")
    public ResponseEntity<ApiResponse<AiJobAcceptedResponse>> requestDraftGeneration(
            @PathVariable Long sectionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        UUID requestId =
                requestService.requestDraftGeneration(sectionId, principal.userId());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(
                        "DRAFT_GENERATION_REQUESTED",
                        "AI 초안 생성을 시작했습니다.",
                        new AiJobAcceptedResponse(requestId)));
    }
}
