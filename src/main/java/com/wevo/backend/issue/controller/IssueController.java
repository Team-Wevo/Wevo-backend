package com.wevo.backend.issue.controller;

import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.issue.dto.request.IssueDecisionRequest;
import com.wevo.backend.issue.dto.response.IssueDecisionResponse;
import com.wevo.backend.issue.service.IssueDecisionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 쟁점 결정·보충 근거 API. */
@RestController
@RequestMapping("/api/issues")
public class IssueController {

    private final IssueDecisionService issueDecisionService;

    public IssueController(IssueDecisionService issueDecisionService) {
        this.issueDecisionService = issueDecisionService;
    }

    /** OWNER가 현재 정리 세트의 CONFLICT 쟁점을 결정한다. */
    @PostMapping("/{issueId}/decision")
    public ResponseEntity<ApiResponse<IssueDecisionResponse>> decideConflict(
            @PathVariable Long issueId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody IssueDecisionRequest request
    ) {
        IssueDecisionResponse response =
                issueDecisionService.decide(issueId, principal.userId(), request);
        return ResponseEntity.ok(
                ApiResponse.success("ISSUE_DECIDED", "쟁점이 결정되었습니다.", response));
    }
}
