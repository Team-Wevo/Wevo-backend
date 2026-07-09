package com.wevo.backend.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 발급/검증에 필요한 설정 값.
 *
 * @param secret                  HMAC 서명 키 (256bit 이상 권장)
 * @param accessTokenValidityMs   Access Token 유효 시간(ms)
 * @param refreshTokenValidityMs  Refresh Token 유효 시간(ms)
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        long accessTokenValidityMs,
        long refreshTokenValidityMs
) {
}
