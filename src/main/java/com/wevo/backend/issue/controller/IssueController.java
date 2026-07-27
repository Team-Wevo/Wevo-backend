package com.wevo.backend.issue.controller;

import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.issue.dto.request.EvidenceRequestCreateRequest;
import com.wevo.backend.issue.dto.request.IssueAnswerRequest;
import com.wevo.backend.issue.dto.request.IssueDecisionRequest;
import com.wevo.backend.issue.dto.response.EvidenceRequestResponse;
import com.wevo.backend.issue.dto.response.IssueAnswerResponse;
import com.wevo.backend.issue.dto.response.IssueDecisionResponse;
import com.wevo.backend.issue.service.EvidenceRequestService;
import com.wevo.backend.issue.service.IssueAnswerService;
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
    private final EvidenceRequestService evidenceRequestService;
    private final IssueAnswerService issueAnswerService;

    public IssueController(
            IssueDecisionService issueDecisionService,
            EvidenceRequestService evidenceRequestService,
            IssueAnswerService issueAnswerService
    ) {
        this.issueDecisionService = issueDecisionService;
        this.evidenceRequestService = evidenceRequestService;
        this.issueAnswerService = issueAnswerService;
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

    /** OWNER가 현재 정리 세트의 GAP 쟁점 관련 의견 작성자에게 추가 근거를 요청한다. */
    @PostMapping("/{issueId}/evidence-request")
    public ResponseEntity<ApiResponse<EvidenceRequestResponse>> requestEvidence(
            @PathVariable Long issueId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody EvidenceRequestCreateRequest request
    ) {
        EvidenceRequestResponse response =
                evidenceRequestService.request(issueId, principal.userId(), request);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "EVIDENCE_REQUESTED",
                        "추가 근거를 요청했습니다.",
                        response));
    }

    /** 지목된 팀원이 현재 정리 세트의 GAP 쟁점에 보충 근거를 답변한다. */
    @PostMapping("/{issueId}/answers")
    public ResponseEntity<ApiResponse<IssueAnswerResponse>> answerEvidenceRequest(
            @PathVariable Long issueId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody IssueAnswerRequest request
    ) {
        IssueAnswerResponse response =
                issueAnswerService.answer(issueId, principal.userId(), request);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "EVIDENCE_ANSWERED",
                        "추가 근거 답변이 등록되었습니다.",
                        response));
    }
}
