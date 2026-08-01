package com.wevo.backend.ai.controller;

import com.wevo.backend.ai.dto.response.AiJobAcceptedResponse;
import com.wevo.backend.ai.dto.response.OpinionClusteringResponse;
import com.wevo.backend.ai.service.OpinionClusteringQueryService;
import com.wevo.backend.ai.service.OpinionClusteringRequestService;
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
@RequestMapping("/api/project-sections")
@Tag(name = "AI Facilitation", description = "AI 정리·분류·초안·사전 검토 API")
public class OpinionClusteringController {

    private final OpinionClusteringRequestService requestService;
    private final OpinionClusteringQueryService queryService;

    public OpinionClusteringController(
            OpinionClusteringRequestService requestService,
            OpinionClusteringQueryService queryService
    ) {
        this.requestService = requestService;
        this.queryService = queryService;
    }

    @PostMapping("/{sectionId}/opinion-clusters")
    @Operation(summary = "AI 의견 자동 분류 실행")
    public ResponseEntity<ApiResponse<AiJobAcceptedResponse>> requestClustering(
            @PathVariable Long sectionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        UUID requestId = requestService.requestClustering(sectionId, principal.userId());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(
                        "OPINION_CLUSTERING_REQUESTED",
                        "AI 의견 분류를 시작했습니다.",
                        new AiJobAcceptedResponse(requestId)
                ));
    }

    @GetMapping("/{sectionId}/opinion-clusters")
    @Operation(summary = "AI 의견 자동 분류 조회")
    public ResponseEntity<ApiResponse<OpinionClusteringResponse>> getClustering(
            @PathVariable Long sectionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "OK",
                "조회에 성공했습니다.",
                queryService.getClustering(sectionId, principal.userId())
        ));
    }
}
