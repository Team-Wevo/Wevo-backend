package com.wevo.backend.ai.controller;

import com.wevo.backend.ai.dto.request.AuthorIntentConfirmRequest;
import com.wevo.backend.ai.dto.response.AiJobAcceptedResponse;
import com.wevo.backend.ai.dto.response.AuthorIntentResponse;
import com.wevo.backend.ai.service.AuthorIntentConfirmationService;
import com.wevo.backend.ai.service.AuthorIntentQueryService;
import com.wevo.backend.ai.service.AuthorIntentRequestService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-sections")
@Tag(name = "AI Facilitation", description = "AI 정리·초안·사전 검토 API")
public class AuthorIntentController {

    private final AuthorIntentRequestService requestService;
    private final AuthorIntentQueryService queryService;
    private final AuthorIntentConfirmationService confirmationService;

    public AuthorIntentController(
            AuthorIntentRequestService requestService,
            AuthorIntentQueryService queryService,
            AuthorIntentConfirmationService confirmationService
    ) {
        this.requestService = requestService;
        this.queryService = queryService;
        this.confirmationService = confirmationService;
    }

    @Operation(summary = "작성자 의도 추출 실행 — 비동기 202, requestId 로 폴링")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "202", description = "AUTHOR_INTENT_EXTRACTION_REQUESTED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "S001 — 섹션 없음 또는 비멤버 (존재 숨김)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503", description = "AI008 — AI Provider 비활성·연결 불가")
    })
    @PostMapping("/{sectionId}/author-intent/extractions")
    public ResponseEntity<ApiResponse<AiJobAcceptedResponse>> extract(
            @PathVariable Long sectionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        UUID requestId = requestService.requestExtraction(sectionId, principal.userId());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(
                        "AUTHOR_INTENT_EXTRACTION_REQUESTED",
                        "작성자 의도 추출을 시작했습니다.",
                        new AiJobAcceptedResponse(requestId)));
    }

    @Operation(summary = "작성자 의도 조회 — 추출 결과와 확정 여부")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "OK"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "S001 — 섹션 없음 또는 비멤버 (존재 숨김)")
    })
    @GetMapping("/{sectionId}/author-intent")
    public ResponseEntity<ApiResponse<AuthorIntentResponse>> getCurrent(
            @PathVariable Long sectionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "OK",
                "조회에 성공했습니다.",
                queryService.getCurrent(sectionId, principal.userId())));
    }

    @Operation(summary = "작성자 의도 확정 — 추출 결과를 수정·확정")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "AUTHOR_INTENT_CONFIRMED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "C001 — 입력 검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "S001 — 섹션 없음 또는 비멤버 (존재 숨김)")
    })
    @PutMapping("/{sectionId}/author-intent")
    public ResponseEntity<ApiResponse<AuthorIntentResponse>> confirm(
            @PathVariable Long sectionId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody AuthorIntentConfirmRequest request
    ) {
        AuthorIntentResponse response = confirmationService.confirm(
                sectionId,
                principal.userId(),
                request.contentVersion(),
                request.intent());
        return ResponseEntity.ok(ApiResponse.success(
                "AUTHOR_INTENT_CONFIRMED",
                "작성자 의도가 확정되었습니다.",
                response));
    }
}
