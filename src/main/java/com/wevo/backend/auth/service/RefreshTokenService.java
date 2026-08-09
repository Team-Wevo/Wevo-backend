package com.wevo.backend.auth.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * Refresh Token 을 Redis 에 저장/회전/폐기한다. (사용자당 1개, 재발급 시 회전·로그아웃 시 폐기)
 *
 * <p>원문이 아니라 SHA-256 해시만 저장한다 — Redis 가 유출돼도 토큰을 그대로 쓸 수 없게 한다.
 */
@Service
public class RefreshTokenService {

    private static final String KEY_PREFIX = "refresh_token:";

    /**
     * 저장된 해시가 제시된 해시와 같을 때만 새 해시로 <b>원자적으로</b> 교체한다.
     *
     * <p>GET 비교 후 SET 하는 방식은 두 명령 사이에 다른 요청이 끼어들 수 있어, 같은 Refresh Token
     * 으로 동시에 재발급하면 <b>양쪽 모두 검증을 통과</b>한다. 회전형 토큰의 핵심 방어("한 번 쓰면
     * 무효")가 경합에서 뚫리므로 하나의 스크립트로 묶는다.
     *
     * <p>해시가 <b>다르면 이미 회전된 옛 토큰</b>이 다시 제시된 것이다 — 정상 클라이언트는 회전 후
     * 옛 토큰을 버리므로, 이는 토큰이 유출돼 한쪽이 먼저 써버린 정황이다. 거부만 하면 탈취자가 쥔
     * 새 토큰은 계속 살아 있으므로 <b>키를 지워 세션 자체를 끊고</b> 양쪽 모두 재로그인시킨다.
     * (RFC 9700 — 회전 토큰 재사용 감지 시 토큰 패밀리 무효화)
     *
     * <p>반환값: {@code 1} 회전 성공, {@code 0} 저장된 토큰 없음(만료·로그아웃·탈퇴 폐기),
     * {@code -1} 재사용 감지(세션 폐기함).
     */
    private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>("""
            local stored = redis.call('GET', KEYS[1])
            if not stored then return 0 end
            if stored ~= ARGV[1] then
              redis.call('DEL', KEYS[1])
              return -1
            end
            redis.call('SET', KEYS[1], ARGV[2], 'PX', tonumber(ARGV[3]))
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RefreshTokenService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 회전 시도 결과. */
    public enum RotationResult {
        /** 제시한 토큰이 현재 토큰과 일치해 새 토큰으로 교체했다. */
        ROTATED,
        /** 저장된 토큰이 없다. (유효 기간 만료·로그아웃·회원 탈퇴로 폐기됨) */
        NOT_FOUND,
        /** 이미 회전된 옛 토큰이 다시 제시됐다. 유출 정황이므로 세션을 폐기했다. */
        REUSE_DETECTED
    }

    public void save(Long userId, String refreshToken, long ttlMs) {
        redisTemplate.opsForValue().set(key(userId), hash(refreshToken), Duration.ofMillis(ttlMs));
    }

    /**
     * 현재 Refresh Token 을 새 토큰으로 원자적으로 교체한다.
     *
     * @param currentRefreshToken 클라이언트가 제시한 토큰 (원문)
     * @param newRefreshToken     교체할 새 토큰 (원문)
     * @param ttlMs               새 토큰의 유효 시간
     */
    public RotationResult rotate(Long userId, String currentRefreshToken, String newRefreshToken, long ttlMs) {
        Long result = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(key(userId)),
                hash(currentRefreshToken),
                hash(newRefreshToken),
                String.valueOf(ttlMs)
        );
        if (result == null) {
            // 스크립트는 항상 숫자를 돌려준다. null 이면 Redis 연결·직렬화 문제이므로 조용히
            // 통과시키지 않고 재발급을 실패시킨다.
            return RotationResult.NOT_FOUND;
        }
        return switch (result.intValue()) {
            case 1 -> RotationResult.ROTATED;
            case -1 -> RotationResult.REUSE_DETECTED;
            default -> RotationResult.NOT_FOUND;
        };
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
