package com.wevo.backend.ai.controller;

import com.wevo.backend.ai.dto.response.SynthesisAcceptedResponse;
import com.wevo.backend.ai.dto.response.SynthesisResponse;
import com.wevo.backend.ai.service.SynthesisQueryService;
import com.wevo.backend.ai.service.SynthesisRequestService;
import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 의견 정리 API. (인증 필요 — 실행은 OWNER 전용 §3.8.1, 조회는 프로젝트 참여자 §3.8.2)
 */
@RestController
@RequestMapping("/api/project-sections")
public class SynthesisController {

    private final SynthesisRequestService synthesisRequestService;
    private final SynthesisQueryService synthesisQueryService;

    public SynthesisController(SynthesisRequestService synthesisRequestService,
                               SynthesisQueryService synthesisQueryService) {
        this.synthesisRequestService = synthesisRequestService;
        this.synthesisQueryService = synthesisQueryService;
    }

    /**
     * 제출된 팀원 의견과 기존 GAP 보충 근거를 AI가 분석해 합의점·쟁점을 정리한다(비동기).
     * 성공 시 {@code 202 Accepted}와 폴링용 {@code requestId}를 반환한다.
     */
    @PostMapping("/{projectSectionId}/synthesis")
    public ResponseEntity<ApiResponse<SynthesisAcceptedResponse>> requestSynthesis(
            @PathVariable Long projectSectionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        UUID requestId = synthesisRequestService.requestSynthesis(projectSectionId, principal.userId());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(
                        "SYNTHESIS_REQUESTED",
                        "AI 의견 정리를 시작했습니다.",
                        new SynthesisAcceptedResponse(requestId)));
    }

    /**
     * 섹션의 합의점·쟁점을 조회한다. 최신 실행 상태와 현재 정리 세트를 분리해 반환하며,
     * 정리 실행 이력이 없으면 오류가 아니라 {@code exists=false}로 응답한다. (§3.8.2)
     */
    @GetMapping("/{projectSectionId}/synthesis")
    public ResponseEntity<ApiResponse<SynthesisResponse>> getSynthesis(
            @PathVariable Long projectSectionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        SynthesisResponse response =
                synthesisQueryService.getSynthesis(projectSectionId, principal.userId());
        return ResponseEntity.ok(ApiResponse.success("OK", "조회에 성공했습니다.", response));
    }
}
