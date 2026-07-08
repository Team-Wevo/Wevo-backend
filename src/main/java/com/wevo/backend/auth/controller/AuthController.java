package com.wevo.backend.auth.controller;

import com.wevo.backend.auth.dto.request.SocialLoginRequest;
import com.wevo.backend.auth.dto.request.TokenReissueRequest;
import com.wevo.backend.auth.dto.response.TokenResponse;
import com.wevo.backend.auth.service.AuthService;
import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 소셜 로그인 — 인가코드로 로그인하고 자체 JWT 를 발급한다. (신규 사용자는 자동 가입)
     */
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
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        authService.logout(principal.userId());
        return ResponseEntity.ok(
                ApiResponse.success("LOGOUT_SUCCESS", "로그아웃되었습니다.", null));
    }
}
