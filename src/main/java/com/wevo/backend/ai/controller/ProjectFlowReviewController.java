package com.wevo.backend.ai.controller;

import com.wevo.backend.ai.dto.response.AiJobAcceptedResponse;
import com.wevo.backend.ai.dto.response.ProjectFlowReviewResponse;
import com.wevo.backend.ai.service.ProjectFlowReviewQueryService;
import com.wevo.backend.ai.service.ProjectFlowReviewRequestService;
import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "AI Facilitation", description = "AI 정리·분류·초안·사전 검토 API")
public class ProjectFlowReviewController {
    private final ProjectFlowReviewRequestService requestService;
    private final ProjectFlowReviewQueryService queryService;
    public ProjectFlowReviewController(ProjectFlowReviewRequestService requestService,
                                       ProjectFlowReviewQueryService queryService) {
        this.requestService = requestService; this.queryService = queryService;
    }
    @PostMapping("/{projectId}/flow-check")
    @Operation(summary = "최종 결과물 전체 흐름 점검 실행")
    public ResponseEntity<ApiResponse<AiJobAcceptedResponse>> request(
            @PathVariable Long projectId, @AuthenticationPrincipal AuthPrincipal principal) {
        UUID requestId = requestService.requestReview(projectId, principal.userId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
                "PROJECT_FLOW_REVIEW_REQUESTED", "전체 흐름 점검을 시작했습니다.",
                new AiJobAcceptedResponse(requestId)));
    }
    @GetMapping("/{projectId}/flow-check")
    @Operation(summary = "최종 결과물 전체 흐름 점검 조회")
    public ResponseEntity<ApiResponse<ProjectFlowReviewResponse>> get(
            @PathVariable Long projectId, @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("OK", "조회에 성공했습니다.",
                queryService.getReview(projectId, principal.userId())));
    }
}
