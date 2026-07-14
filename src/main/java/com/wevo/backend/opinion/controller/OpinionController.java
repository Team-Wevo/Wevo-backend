package com.wevo.backend.opinion.controller;

import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.opinion.dto.request.OpinionDraftRequest;
import com.wevo.backend.opinion.dto.response.MyOpinionResponse;
import com.wevo.backend.opinion.dto.response.OpinionDraftResponse;
import com.wevo.backend.opinion.dto.response.OpinionSubmitResponse;
import com.wevo.backend.opinion.dto.response.SubmittedOpinionListResponse;
import com.wevo.backend.opinion.service.OpinionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 의견(my-opinion) API. (프로젝트 멤버 전용 — 인증 필요)
 */
@RestController
@RequestMapping("/api/project-sections")
public class OpinionController {

    private final OpinionService opinionService;

    public OpinionController(OpinionService opinionService) {
        this.opinionService = opinionService;
    }

    /**
     * 내 의견 상태를 조회한다. 아직 작성 전이면 404가 아니라 {@code exists=false} 로 응답한다.
     */
    @GetMapping("/{projectSectionId}/my-opinion")
    public ResponseEntity<ApiResponse<MyOpinionResponse>> getMyOpinion(
            @PathVariable Long projectSectionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        MyOpinionResponse response = opinionService.getMyOpinion(projectSectionId, principal.userId());
        return ResponseEntity.ok(ApiResponse.success("OK", "조회에 성공했습니다.", response));
    }

    /**
     * 내 의견을 임시저장한다. 의견이 없으면 새로 만들고, 있으면 덮어쓴다(upsert).
     */
    @PatchMapping("/{projectSectionId}/my-opinion/draft")
    public ResponseEntity<ApiResponse<OpinionDraftResponse>> saveDraft(
            @PathVariable Long projectSectionId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody OpinionDraftRequest request
    ) {
        OpinionDraftResponse response = opinionService.saveDraft(projectSectionId, principal.userId(), request);
        return ResponseEntity.ok(ApiResponse.success("OPINION_DRAFT_SAVED", "의견이 임시저장되었습니다.", response));
    }

    /**
     * 섹션에 제출된 팀원 의견 목록을 조회한다.
     * 본인이 제출한 적 없으면 목록은 숨기고 제출 건수만 반환한다(공개 게이트).
     */
    @GetMapping("/{projectSectionId}/opinions")
    public ResponseEntity<ApiResponse<SubmittedOpinionListResponse>> getSubmittedOpinions(
            @PathVariable Long projectSectionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        SubmittedOpinionListResponse response =
                opinionService.getSubmittedOpinions(projectSectionId, principal.userId());
        return ResponseEntity.ok(ApiResponse.success("OK", "조회에 성공했습니다.", response));
    }

    /**
     * 임시저장된 내 의견을 팀에 제출한다. 이미 제출된 의견은 멱등하게 성공 응답을 반환한다.
     */
    @PostMapping("/{projectSectionId}/my-opinion/submit")
    public ResponseEntity<ApiResponse<OpinionSubmitResponse>> submitMyOpinion(
            @PathVariable Long projectSectionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        OpinionSubmitResponse response = opinionService.submitMyOpinion(projectSectionId, principal.userId());
        return ResponseEntity.ok(ApiResponse.success("OPINION_SUBMITTED", "의견이 제출되었습니다.", response));
    }
}
