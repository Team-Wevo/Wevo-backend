package com.wevo.backend.section.controller;

import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.section.dto.response.DraftLeaseAcquireResponse;
import com.wevo.backend.section.dto.response.DraftLeaseRenewResponse;
import com.wevo.backend.section.dto.response.DraftLeaseStatusResponse;
import com.wevo.backend.section.service.DraftLeaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 섹션 초안 편집 잠금 API.
 */
@RestController
@RequestMapping("/api/project-sections")
@Tag(name = "Sections", description = "섹션 초안·편집 잠금·검토 요청·확정 API")
public class DraftLeaseController {

    private final DraftLeaseService draftLeaseService;

    public DraftLeaseController(DraftLeaseService draftLeaseService) {
        this.draftLeaseService = draftLeaseService;
    }

    @Operation(summary = "편집 시작 — 잠금 획득 (TTL 5분, 본인 보유 시 멱등 연장)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "DRAFT_LEASE_ACQUIRED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "S001 — 섹션 없음 또는 비멤버 (존재 숨김) / S003 — 초안 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "S002 — 편집 불가 상태 / S004 — 타인이 편집권 보유")
    })
    @PostMapping("/{sectionId}/draft/lease")
    public ResponseEntity<ApiResponse<DraftLeaseAcquireResponse>> acquire(
            @PathVariable Long sectionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        DraftLeaseAcquireResponse response =
                draftLeaseService.acquire(sectionId, principal.userId());
        return ResponseEntity.ok(
                ApiResponse.success("DRAFT_LEASE_ACQUIRED", "편집권을 획득했습니다.", response)
        );
    }

    @Operation(summary = "편집 잠금 현황 조회 — 편집자·만료 시각")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "OK"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "S001 — 섹션 없음 또는 비멤버 (존재 숨김)")
    })
    @GetMapping("/{sectionId}/draft/lease")
    public ResponseEntity<ApiResponse<DraftLeaseStatusResponse>> getStatus(
            @PathVariable Long sectionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        DraftLeaseStatusResponse response =
                draftLeaseService.getStatus(sectionId, principal.userId());
        return ResponseEntity.ok(ApiResponse.success("OK", "조회에 성공했습니다.", response));
    }

    @Operation(summary = "heartbeat — 편집권 만료 시각 연장 (편집 화면이 주기 호출)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "DRAFT_LEASE_RENEWED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "S001 — 섹션 없음 또는 비멤버 (존재 숨김)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "S004 — 만료 사이 타인이 획득 / S005 — 편집권 미보유")
    })
    @PutMapping("/{sectionId}/draft/lease")
    public ResponseEntity<ApiResponse<DraftLeaseRenewResponse>> renew(
            @PathVariable Long sectionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        DraftLeaseRenewResponse response =
                draftLeaseService.renew(sectionId, principal.userId());
        return ResponseEntity.ok(
                ApiResponse.success("DRAFT_LEASE_RENEWED", "편집권이 연장되었습니다.", response));
    }

    @Operation(summary = "편집 종료 — 저장 없이 편집권 해제 (타인 잠금 강제 해제 불가)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "DRAFT_LEASE_RELEASED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "S001 — 섹션 없음 또는 비멤버 (존재 숨김)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "S004 — 타인이 편집권 보유 / S005 — 편집권 미보유")
    })
    @DeleteMapping("/{sectionId}/draft/lease")
    public ResponseEntity<ApiResponse<Void>> release(
            @PathVariable Long sectionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        draftLeaseService.release(sectionId, principal.userId());
        return ResponseEntity.ok(
                ApiResponse.success("DRAFT_LEASE_RELEASED", "편집을 종료했습니다.", null));
    }
}
