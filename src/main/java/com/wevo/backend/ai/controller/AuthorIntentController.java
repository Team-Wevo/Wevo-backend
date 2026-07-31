package com.wevo.backend.ai.controller;

import com.wevo.backend.ai.dto.request.AuthorIntentConfirmRequest;
import com.wevo.backend.ai.dto.response.AiJobAcceptedResponse;
import com.wevo.backend.ai.dto.response.AuthorIntentResponse;
import com.wevo.backend.ai.service.AuthorIntentConfirmationService;
import com.wevo.backend.ai.service.AuthorIntentQueryService;
import com.wevo.backend.ai.service.AuthorIntentRequestService;
import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
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
