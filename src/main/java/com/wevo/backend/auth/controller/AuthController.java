package com.wevo.backend.auth.controller;

import com.wevo.backend.auth.dto.request.SocialLoginRequest;
import com.wevo.backend.auth.dto.request.TokenReissueRequest;
import com.wevo.backend.auth.dto.response.TokenResponse;
import com.wevo.backend.auth.service.AuthService;
import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "소셜 로그인·토큰 재발급·로그아웃 API")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 소셜 로그인 — 인가코드로 로그인하고 자체 JWT 를 발급한다. (신규 사용자는 자동 가입)
     */
    @Operation(summary = "소셜 로그인 — 인가코드로 JWT 발급 (최초 로그인 시 자동 가입)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "LOGIN_SUCCESS"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "C001 — 입력 검증 실패 / A006 — 지원하지 않는 제공자"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "U002 — 이미 다른 방식으로 가입된 이메일"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "A007 — 소셜 제공자 토큰 교환·조회 실패")
    })
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody SocialLoginRequest request
    ) {
        TokenResponse tokens = authService.login(
                request.provider(), request.code(), request.redirectUri());
        return ResponseEntity.ok(
                ApiResponse.success("LOGIN_SUCCESS", "로그인에 성공했습니다.", tokens));
    }

    /**
     * Access Token 재발급 — Refresh Token 으로 Access/Refresh 를 재발급한다.
     */
    @Operation(summary = "Access Token 재발급 — Refresh Token 사용")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "TOKEN_REISSUED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "C001 — refreshToken 누락"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "A003 — 형식·서명 오류 / A004 — 만료 / A005 — 저장된 토큰과 불일치")
    })
    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<TokenResponse>> reissue(
            @Valid @RequestBody TokenReissueRequest request
    ) {
        TokenResponse tokens = authService.reissue(request.refreshToken());
        return ResponseEntity.ok(
                ApiResponse.success("TOKEN_REISSUED", "토큰이 재발급되었습니다.", tokens));
    }

    /**
     * 로그아웃 — 저장된 Refresh Token 을 폐기한다.
     */
    @Operation(summary = "로그아웃 — Refresh Token 폐기 (요청 본문 없음)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "LOGOUT_SUCCESS"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요")
    })
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        authService.logout(principal.userId());
        return ResponseEntity.ok(
                ApiResponse.success("LOGOUT_SUCCESS", "로그아웃되었습니다.", null));
    }
}
