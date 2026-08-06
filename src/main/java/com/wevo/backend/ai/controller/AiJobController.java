package com.wevo.backend.ai.controller;

import com.wevo.backend.ai.dto.response.AiJobResponse;
import com.wevo.backend.ai.service.AiJobQueryService;
import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 비동기 AI 작업의 외부 상태를 조회하는 공통 polling API. */
@RestController
@RequestMapping("/api/ai-jobs")
@Tag(name = "AI Jobs", description = "비동기 AI 작업 polling API")
public class AiJobController {

    private final AiJobQueryService queryService;

    public AiJobController(AiJobQueryService queryService) {
        this.queryService = queryService;
    }

    @Operation(
            summary = "비동기 AI 작업 상태 조회",
            description = """
                    작업이 속한 프로젝트의 모든 멤버가 조회할 수 있습니다. 내부 상태는
                    REQUESTED/SUCCEEDED/FAILED로 변환하고 결과 본문은 기능별 조회 API에서 제공합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "OK"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "C001 — requestId 형식 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "AI021 — 작업 없음 또는 비멤버")
    })
    @GetMapping("/{requestId}")
    public ResponseEntity<ApiResponse<AiJobResponse>> getJob(
            @PathVariable UUID requestId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        AiJobResponse response = queryService.getJob(requestId, principal.userId());
        return ResponseEntity.ok(ApiResponse.success("OK", "조회에 성공했습니다.", response));
    }
}
