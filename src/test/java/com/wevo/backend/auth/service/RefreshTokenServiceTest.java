package com.wevo.backend.auth.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * 토큰 해싱과 회전 결과 매핑의 단위 검증.
 *
 * <p>회전의 <b>원자성</b>과 재사용 감지는 Lua 스크립트가 수행하므로 목으로는 검증되지 않는다.
 * 실제 Redis 동작은 {@code RefreshTokenRotationRedisIntegrationTest} 가 본다.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void saveStoresHashedRefreshToken() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        RefreshTokenService refreshTokenService = new RefreshTokenService(redisTemplate);

        refreshTokenService.save(1L, "plain-refresh-token", 1_000L);

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(
                anyString(),
                valueCaptor.capture(),
                eq(java.time.Duration.ofMillis(1_000L))
        );

        assertThat(valueCaptor.getValue())
                .isEqualTo(hash("plain-refresh-token"))
                .isNotEqualTo("plain-refresh-token");
    }

    @Test
    void deleteRemovesUserKey() {
        RefreshTokenService refreshTokenService = new RefreshTokenService(redisTemplate);

        refreshTokenService.delete(7L);

        verify(redisTemplate).delete("refresh_token:7");
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder()
                    .encodeToString(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
