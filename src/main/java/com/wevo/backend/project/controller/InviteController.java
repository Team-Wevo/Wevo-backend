package com.wevo.backend.project.controller;

import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.project.dto.response.InviteLinkResponse;
import com.wevo.backend.project.dto.response.InvitePreviewResponse;
import com.wevo.backend.project.dto.response.ProjectJoinResponse;
import com.wevo.backend.project.service.InviteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 프로젝트 초대 링크 생성·미리보기·참여 API. (제품 정책서 §2.1)
 *
 * <p>생성은 프로젝트 하위 경로, 미리보기·참여는 토큰이 리소스를 식별하므로 {@code /api/invites} 하위에 둔다.
 */
@RestController
@Tag(name = "Invites", description = "초대 링크 생성·미리보기·참여 API")
public class InviteController {

    private final InviteService inviteService;

    public InviteController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    /**
     * 초대 링크 생성 — OWNER 만 호출 가능. 활성 링크가 있으면 그대로 반환한다(재사용).
     */
    @Operation(summary = "초대 링크 생성 — 재사용 링크, OWNER 만. 활성 링크가 있으면 그대로 반환(멱등)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "INVITE_LINK_CREATED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "A002 — 멤버지만 OWNER 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "P001 — 프로젝트 없음 또는 비멤버 (존재 숨김)")
    })
    @PostMapping("/api/projects/{projectId}/invites")
    public ResponseEntity<ApiResponse<InviteLinkResponse>> createInviteLink(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long projectId
    ) {
        InviteLinkResponse response = inviteService.createInviteLink(principal.userId(), projectId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("INVITE_LINK_CREATED", "초대 링크가 생성되었습니다.", response));
    }

    /**
     * 참여 전 미리보기 — 유효한 토큰이면 프로젝트 정보를 반환한다.
     */
    @Operation(summary = "참여 전 미리보기 — 프로젝트명·현재 인원 (로그인 필요)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "OK"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "P003 — 유효하지 않은 초대 링크")
    })
    @GetMapping("/api/invites/{token}")
    public ResponseEntity<ApiResponse<InvitePreviewResponse>> getInvitePreview(
            @PathVariable String token
    ) {
        InvitePreviewResponse response = inviteService.getInvitePreview(token);
        return ResponseEntity.ok(ApiResponse.success("OK", "조회에 성공했습니다.", response));
    }

    /**
     * 초대 링크로 프로젝트 참여 — MEMBER 로 참여한다. 이미 멤버면 멱등하게 성공한다.
     */
    @Operation(summary = "초대 링크로 참여 — MEMBER 로 참여. 이미 멤버면 기존 역할로 멱등 성공")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "PROJECT_JOINED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "P003 — 유효하지 않은 초대 링크"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "P004 — 프로젝트 정원(4명) 초과")
    })
    @PostMapping("/api/invites/{token}/join")
    public ResponseEntity<ApiResponse<ProjectJoinResponse>> join(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String token
    ) {
        ProjectJoinResponse response = inviteService.joinByToken(principal.userId(), token);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("PROJECT_JOINED", "프로젝트에 참여했습니다.", response));
    }
}
