package com.wevo.backend.auth.controller;

import com.wevo.backend.auth.dto.request.DevLoginRequest;
import com.wevo.backend.auth.dto.response.TokenResponse;
import com.wevo.backend.auth.service.DevLoginService;
import com.wevo.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개발 편의용 임시 로그인 컨트롤러. (local 프로파일 전용 — 운영에서는 등록되지 않음)
 *
 * <p>실제 소셜 인증 없이 Swagger/Postman 으로 바로 토큰을 발급받아 보호 API 를 테스트할 수 있다.
 */
@RestController
@RequestMapping("/api/auth")
@Profile("local")
@Tag(name = "Auth", description = "소셜 로그인·토큰 재발급·로그아웃 API")
public class DevAuthController {

    private static final String DEFAULT_EMAIL = "dev@wevo.com";
    private static final String DEFAULT_NAME = "개발용 사용자";

    private final DevLoginService devLoginService;

    public DevAuthController(DevLoginService devLoginService) {
        this.devLoginService = devLoginService;
    }

    /**
     * 임시 로그인 — 지정한(생략 시 기본) 사용자로 JWT 를 발급한다.
     */
    @Operation(summary = "임시 로그인 (개발 전용 · local 프로파일) — 소셜 인증 없이 JWT 발급")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "DEV_LOGIN_SUCCESS"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "local 외 프로파일에서는 컨트롤러가 등록되지 않아 경로 자체가 없음")
    })
    @PostMapping("/dev-login")
    public ResponseEntity<ApiResponse<TokenResponse>> devLogin(
            @RequestBody(required = false) DevLoginRequest request
    ) {
        String email = (request != null && StringUtils.hasText(request.email()))
                ? request.email() : DEFAULT_EMAIL;
        String name = (request != null && StringUtils.hasText(request.name()))
                ? request.name() : DEFAULT_NAME;

        TokenResponse tokens = devLoginService.devLogin(email, name);
        return ResponseEntity.ok(
                ApiResponse.success("DEV_LOGIN_SUCCESS", "임시 로그인에 성공했습니다.", tokens));
    }
}
