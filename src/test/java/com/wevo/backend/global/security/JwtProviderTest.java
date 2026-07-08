package com.wevo.backend.global.security;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtProviderTest {

    private static final String SECRET = "test-secret-key-for-wevo-backend-that-is-long-enough-000000";
    private static final long ACCESS_VALIDITY_MS = 1_800_000L;
    private static final long REFRESH_VALIDITY_MS = 1_209_600_000L;

    private final JwtProvider jwtProvider =
            new JwtProvider(new JwtProperties(SECRET, ACCESS_VALIDITY_MS, REFRESH_VALIDITY_MS));

    @Test
    @DisplayName("Access Token 을 ACCESS 로 파싱하면 동일한 userId 를 복원한다")
    void createAccessToken_thenParseAsAccess_returnsSameUserId() {
        Long userId = 42L;

        String token = jwtProvider.createAccessToken(userId);

        assertThat(jwtProvider.parseUserId(token, TokenType.ACCESS)).isEqualTo(userId);
    }

    @Test
    @DisplayName("Refresh Token 을 REFRESH 로 파싱하면 동일한 userId 를 복원한다")
    void createRefreshToken_thenParseAsRefresh_returnsSameUserId() {
        Long userId = 7L;

        String token = jwtProvider.createRefreshToken(userId);

        assertThat(jwtProvider.parseUserId(token, TokenType.REFRESH)).isEqualTo(userId);
    }

    @Test
    @DisplayName("Refresh Token 을 ACCESS 용도로 파싱하면 INVALID_TOKEN 예외를 던진다")
    void parseRefreshTokenAsAccess_throwsInvalidToken() {
        String refreshToken = jwtProvider.createRefreshToken(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> jwtProvider.parseUserId(refreshToken, TokenType.ACCESS));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("Access Token 을 REFRESH 용도로 파싱하면 INVALID_TOKEN 예외를 던진다")
    void parseAccessTokenAsRefresh_throwsInvalidToken() {
        String accessToken = jwtProvider.createAccessToken(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> jwtProvider.parseUserId(accessToken, TokenType.REFRESH));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("만료된 토큰을 파싱하면 EXPIRED_TOKEN 예외를 던진다")
    void parseUserId_withExpiredToken_throwsExpiredToken() {
        JwtProvider expiringProvider =
                new JwtProvider(new JwtProperties(SECRET, -1_000L, -1_000L));
        String expiredToken = expiringProvider.createAccessToken(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> jwtProvider.parseUserId(expiredToken, TokenType.ACCESS));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EXPIRED_TOKEN);
    }

    @Test
    @DisplayName("형식이 잘못된 토큰을 파싱하면 INVALID_TOKEN 예외를 던진다")
    void parseUserId_withMalformedToken_throwsInvalidToken() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> jwtProvider.parseUserId("not-a-jwt", TokenType.ACCESS));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰을 파싱하면 INVALID_TOKEN 예외를 던진다")
    void parseUserId_withWrongSignature_throwsInvalidToken() {
        JwtProvider otherProvider = new JwtProvider(new JwtProperties(
                "another-secret-key-that-is-also-long-enough-1234567890", ACCESS_VALIDITY_MS, REFRESH_VALIDITY_MS));
        String foreignToken = otherProvider.createAccessToken(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> jwtProvider.parseUserId(foreignToken, TokenType.ACCESS));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("설정한 Refresh Token 유효 시간을 반환한다")
    void getRefreshTokenValidityMs_returnsConfiguredValue() {
        assertThat(jwtProvider.getRefreshTokenValidityMs()).isEqualTo(REFRESH_VALIDITY_MS);
    }
}
