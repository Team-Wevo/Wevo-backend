package com.wevo.backend.ai.controller;

import com.wevo.backend.ai.dto.response.SynthesisAcceptedResponse;
import com.wevo.backend.ai.service.SynthesisRequestService;
import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 의견 정리 실행 API. (프로젝트 OWNER 전용 — 인증 필요, §3.8.1)
 */
@RestController
@RequestMapping("/api/project-sections")
public class SynthesisController {

    private final SynthesisRequestService synthesisRequestService;

    public SynthesisController(SynthesisRequestService synthesisRequestService) {
        this.synthesisRequestService = synthesisRequestService;
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
}
