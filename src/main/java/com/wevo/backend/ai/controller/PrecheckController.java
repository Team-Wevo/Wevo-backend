package com.wevo.backend.ai.controller;

import com.wevo.backend.ai.dto.request.PrecheckRewriteApplyRequest;
import com.wevo.backend.ai.dto.response.AiJobAcceptedResponse;
import com.wevo.backend.ai.dto.response.PrecheckResponse;
import com.wevo.backend.ai.dto.response.PrecheckRewriteApplyResponse;
import com.wevo.backend.ai.service.PrecheckQueryService;
import com.wevo.backend.ai.service.PrecheckRequestService;
import com.wevo.backend.ai.service.PrecheckRewriteApplyResult;
import com.wevo.backend.ai.service.PrecheckRewriteService;
import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 섹션 초안의 AI 사전 검토 실행·조회·수정안 적용 API. */
@RestController
@RequestMapping("/api/project-sections")
@Tag(name = "AI Facilitation", description = "AI 정리·초안·사전 검토 API")
public class PrecheckController {

    private final PrecheckRequestService requestService;
    private final PrecheckQueryService queryService;
    private final PrecheckRewriteService rewriteService;

    public PrecheckController(
            PrecheckRequestService requestService,
            PrecheckQueryService queryService,
            PrecheckRewriteService rewriteService
    ) {
        this.requestService = requestService;
        this.queryService = queryService;
        this.rewriteService = rewriteService;
    }

    @Operation(
            summary = "AI 사전 검토 실행",
            description = """
                    프로젝트 멤버가 현재 초안과 상위 section snapshot 기준의 비동기 사전 검토를
                    요청합니다. 동일 snapshot 재사용도 202이며 공통 job API로 polling합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "202", description = "PRECHECK_REQUESTED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "S001 — 존재 숨김, S003 — 초안 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "S002 — 실행할 수 없는 섹션 상태"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503", description = "AI008 — AI Provider 비활성·연결 불가")
    })
    @PostMapping("/{sectionId}/precheck")
    public ResponseEntity<ApiResponse<AiJobAcceptedResponse>> requestPrecheck(
            @PathVariable Long sectionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        UUID requestId = requestService.requestPrecheck(sectionId, principal.userId());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(
                        "PRECHECK_REQUESTED",
                        "AI 사전 검토를 시작했습니다.",
                        new AiJobAcceptedResponse(requestId)));
    }

    @Operation(
            summary = "최신 AI 사전 검토 조회",
            description = "최신 실행과 마지막 성공 결과를 분리해 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "OK"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "S001 — 섹션 없음 또는 비멤버")
    })
    @GetMapping("/{sectionId}/precheck")
    public ResponseEntity<ApiResponse<PrecheckResponse>> getPrecheck(
            @PathVariable Long sectionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        PrecheckResponse response = queryService.getPrecheck(sectionId, principal.userId());
        return ResponseEntity.ok(ApiResponse.success("OK", "조회에 성공했습니다.", response));
    }

    @Operation(
            summary = "AI 사전 검토 수정안 전체 적용",
            description = """
                    lease 보유자가 화면의 requestId와 checkedContentVersion에 바인딩된 전체 수정안을
                    적용합니다. 최초 성공이 lease를 해제하므로 같은 성공 요청은 lease 검사 전에
                    멱등 성공으로 판정합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "PRECHECK_REWRITE_APPLIED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "C001 — 요청 값 누락·형식 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "S001 — 섹션 없음 또는 비멤버"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "C003 — version/snapshot 충돌, S004/S005 — lease 충돌")
    })
    @PostMapping("/{sectionId}/precheck/apply")
    public ResponseEntity<ApiResponse<PrecheckRewriteApplyResponse>> applyRewrite(
            @PathVariable Long sectionId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody PrecheckRewriteApplyRequest request
    ) {
        PrecheckRewriteApplyResult result = rewriteService.apply(
                sectionId,
                principal.userId(),
                request.requestId(),
                request.checkedContentVersion()
        );
        return ResponseEntity.ok(ApiResponse.success(
                "PRECHECK_REWRITE_APPLIED",
                "AI 수정안이 적용되었습니다.",
                PrecheckRewriteApplyResponse.from(result)));
    }
}
