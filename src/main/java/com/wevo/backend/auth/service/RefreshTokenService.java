package com.wevo.backend.auth.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;

/**
 * Refresh Token 을 Redis 에 저장/검증/폐기한다. (사용자당 1개, 재발급/로그아웃 시 폐기)
 */
@Service
public class RefreshTokenService {

    private static final String KEY_PREFIX = "refresh_token:";

    private final StringRedisTemplate redisTemplate;

    public RefreshTokenService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void save(Long userId, String refreshToken, long ttlMs) {
        redisTemplate.opsForValue().set(key(userId), hash(refreshToken), Duration.ofMillis(ttlMs));
    }

    public boolean matches(Long userId, String refreshToken) {
        String stored = redisTemplate.opsForValue().get(key(userId));
        return stored != null && stored.equals(hash(refreshToken));
    }

    public void delete(Long userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }

    private String hash(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder()
                    .encodeToString(digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", e);
        }
    }
}
