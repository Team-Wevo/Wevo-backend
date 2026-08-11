package com.wevo.backend.opinion.controller;

import com.wevo.backend.global.config.ApiExampleRefs;
import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.opinion.dto.request.OpinionDraftRequest;
import com.wevo.backend.opinion.dto.response.MyOpinionResponse;
import com.wevo.backend.opinion.dto.response.OpinionCollectionStatusResponse;
import com.wevo.backend.opinion.dto.response.OpinionDraftResponse;
import com.wevo.backend.opinion.dto.response.OpinionGateCloseResponse;
import com.wevo.backend.opinion.dto.response.OpinionGateReopenResponse;
import com.wevo.backend.opinion.dto.response.OpinionSubmitResponse;
import com.wevo.backend.opinion.dto.response.SubmittedOpinionListResponse;
import com.wevo.backend.opinion.service.OpinionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Opinions", description = "의견 작성·제출·수집 게이트 API")
public class OpinionController {

    private final OpinionService opinionService;

    public OpinionController(OpinionService opinionService) {
        this.opinionService = opinionService;
    }

    /**
     * 내 의견 상태를 조회한다. 아직 작성 전이면 404가 아니라 {@code exists=false} 로 응답한다.
     */
    @Operation(summary = "내 작업본 조회 — 상태·재제출 필요 여부 포함")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "OK"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "S001 — 섹션 없음 또는 비멤버 (존재 숨김)")
    })
    @GetMapping("/{sectionId}/my-opinion")
    public ResponseEntity<ApiResponse<MyOpinionResponse>> getMyOpinion(
            @PathVariable Long sectionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        MyOpinionResponse response = opinionService.getMyOpinion(sectionId, principal.userId());
        return ResponseEntity.ok(ApiResponse.success("OK", "조회에 성공했습니다.", response));
    }

    /**
     * 내 의견을 임시저장한다. 의견이 없으면 새로 만들고, 있으면 덮어쓴다(upsert).
     */
    @Operation(summary = "내 작업본 임시저장 — 제출본은 그대로 유지 (0~1,000자)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "OPINION_DRAFT_SAVED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "C001 — content 누락·null·1,000자 초과",
                    content = @Content(examples = @ExampleObject(
                            name = "C001", ref = ApiExampleRefs.INVALID_INPUT))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요",
                    content = @Content(examples = @ExampleObject(
                            name = "A001", ref = ApiExampleRefs.UNAUTHORIZED))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "S001 — 섹션 없음 또는 비멤버 (존재 숨김)",
                    content = @Content(examples = @ExampleObject(
                            name = "S001", ref = ApiExampleRefs.SECTION_NOT_FOUND))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "O003 — 의견 수집 마감됨")
    })
    @PatchMapping("/{sectionId}/my-opinion/draft")
    public ResponseEntity<ApiResponse<OpinionDraftResponse>> saveDraft(
            @PathVariable Long sectionId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody OpinionDraftRequest request
    ) {
        OpinionDraftResponse response = opinionService.saveDraft(sectionId, principal.userId(), request);
        return ResponseEntity.ok(ApiResponse.success("OPINION_DRAFT_SAVED", "의견이 임시저장되었습니다.", response));
    }

    /**
     * 섹션에 제출된 팀원 의견 목록을 조회한다.
     * 본인이 제출한 적 없으면 목록은 숨기고 제출 건수만 반환한다(공개 게이트).
     */
    @Operation(summary = "제출된 팀원 의견 목록 — 본인 미제출 시 건수만 공개 (공개 게이트)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "OK"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "S001 — 섹션 없음 또는 비멤버 (존재 숨김)")
    })
    @GetMapping("/{sectionId}/opinions")
    public ResponseEntity<ApiResponse<SubmittedOpinionListResponse>> getSubmittedOpinions(
            @PathVariable Long sectionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        SubmittedOpinionListResponse response =
                opinionService.getSubmittedOpinions(sectionId, principal.userId());
        return ResponseEntity.ok(ApiResponse.success("OK", "조회에 성공했습니다.", response));
    }

    /**
     * 섹션의 의견 수집 현황을 조회한다. (제출 N/M — 팀장은 멤버별 진행 상태까지)
     *
     * <p>참여자 전체가 호출할 수 있지만 응답 범위는 역할로 갈린다 — 멤버별 상태({@code items})는
     * 팀장(OWNER)에게만 내려간다. (정책서 §4.3·§4.5)
     */
    @Operation(summary = "의견 수집 현황 — 제출 N/M·미제출 인원 (본문 미포함). "
            + "멤버별 진행 상태(items)는 팀장에게만 내려간다")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "OK"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요",
                    content = @Content(examples = @ExampleObject(
                            name = "A001", ref = ApiExampleRefs.UNAUTHORIZED))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "S001 — 섹션 없음 또는 비멤버 (존재 숨김)",
                    content = @Content(examples = @ExampleObject(
                            name = "S001", ref = ApiExampleRefs.SECTION_NOT_FOUND)))
    })
    @GetMapping("/{sectionId}/opinion-collection-status")
    public ResponseEntity<ApiResponse<OpinionCollectionStatusResponse>> getCollectionStatus(
            @PathVariable Long sectionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        OpinionCollectionStatusResponse response =
                opinionService.getCollectionStatus(sectionId, principal.userId());
        return ResponseEntity.ok(ApiResponse.success("OK", "조회에 성공했습니다.", response));
    }

    /**
     * 임시저장된 내 의견을 팀에 제출한다. 이미 제출된 의견은 멱등하게 성공 응답을 반환한다.
     */
    @Operation(summary = "내 의견 제출 — 재제출 시 제출본 갱신 (20자 이상, 요청 본문 없음)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "OPINION_SUBMITTED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요",
                    content = @Content(examples = @ExampleObject(
                            name = "A001", ref = ApiExampleRefs.UNAUTHORIZED))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "S001 — 섹션 없음 또는 비멤버 / O001 — 임시저장한 의견 없음",
                    content = @Content(examples = @ExampleObject(
                            name = "S001", ref = ApiExampleRefs.SECTION_NOT_FOUND))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "O003 — 의견 수집 마감됨",
                    content = @Content(examples = @ExampleObject(
                            name = "C003", ref = ApiExampleRefs.CONFLICT))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "422", description = "C002 — 제출 기준(20자) 미달")
    })
    @PostMapping("/{sectionId}/my-opinion/submit")
    public ResponseEntity<ApiResponse<OpinionSubmitResponse>> submitMyOpinion(
            @PathVariable Long sectionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        OpinionSubmitResponse response = opinionService.submitMyOpinion(sectionId, principal.userId());
        return ResponseEntity.ok(ApiResponse.success("OPINION_SUBMITTED", "의견이 제출되었습니다.", response));
    }

    @Operation(summary = "의견 수집 마감 — COLLECTING → SYNTHESIZING (OWNER 만)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "OPINION_GATE_CLOSED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "A002 — 멤버지만 OWNER 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "S001 — 섹션 없음 또는 비멤버 (존재 숨김)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "O004 — 제출된 의견 없음 / S002 — COLLECTING 아님")
    })
    @PostMapping("/{sectionId}/opinion-gate/close")
    public ResponseEntity<ApiResponse<OpinionGateCloseResponse>> closeOpinionGate(
            @PathVariable Long sectionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        OpinionGateCloseResponse response = opinionService.closeOpinionGate(sectionId, principal.userId());
        return ResponseEntity.ok(ApiResponse.success("OPINION_GATE_CLOSED", "의견 수집이 마감되었습니다.", response));
    }

    @Operation(summary = "의견 수집 재오픈 — 미확정 상태 → COLLECTING (OWNER 만). 기존 정리 있으면 synthesisStale=true")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "OPINION_GATE_REOPENED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "A002 — 멤버지만 OWNER 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "S001 — 섹션 없음 또는 비멤버 (존재 숨김)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "S002 — 이미 열림 또는 CONFIRMED / S004 — 타인이 편집 중")
    })
    @PostMapping("/{sectionId}/opinion-gate/reopen")
    public ResponseEntity<ApiResponse<OpinionGateReopenResponse>> reopenOpinionGate(
            @PathVariable Long sectionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        OpinionGateReopenResponse response = opinionService.reopenOpinionGate(sectionId, principal.userId());
        return ResponseEntity.ok(ApiResponse.success("OPINION_GATE_REOPENED", "의견 수집이 다시 열렸습니다.", response));
    }
}
