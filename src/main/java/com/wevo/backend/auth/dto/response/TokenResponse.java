package com.wevo.backend.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
/**
 * 발급된 토큰 응답.
 *
 * @param accessToken  API 호출용 Access Token
 * @param refreshToken Access Token 재발급용 Refresh Token
 */
@Schema(requiredProperties = {"accessToken", "refreshToken"})
public record TokenResponse(
        String accessToken,
        String refreshToken
) {
}
