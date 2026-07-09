package com.wevo.backend.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Access Token 재발급 요청.
 *
 * @param refreshToken 로그인 시 발급받은 Refresh Token
 */
public record TokenReissueRequest(
        @NotBlank String refreshToken
) {
}
