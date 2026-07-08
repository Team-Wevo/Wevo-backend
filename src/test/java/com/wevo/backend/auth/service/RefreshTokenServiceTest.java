package com.wevo.backend.auth.service;

import org.junit.jupiter.api.BeforeEach;
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

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        refreshTokenService = new RefreshTokenService(redisTemplate);
    }

    @Test
    void saveStoresHashedRefreshToken() {
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
    void matchesComparesHashedRefreshToken() {
        given(valueOperations.get("refresh_token:1"))
                .willReturn(hash("plain-refresh-token"));

        boolean matches = refreshTokenService.matches(1L, "plain-refresh-token");

        assertThat(matches).isTrue();
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
