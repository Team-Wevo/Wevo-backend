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
    /** 저장값 {@code familyId:hash} 의 구분자. familyId(UUID)·hash(Base64) 어느 쪽에도 나오지 않는다. */
    private static final String VALUE_DELIMITER = ":";

    /**
     * 저장된 값 {@code familyId:hash} 을 제시된 계보·해시와 비교해 원자적으로 회전한다.
     *
     * <p>GET 비교 후 SET 하는 방식은 두 명령 사이에 다른 요청이 끼어들 수 있어, 같은 Refresh Token
     * 으로 동시에 재발급하면 <b>양쪽 모두 검증을 통과</b>한다. 회전형 토큰의 핵심 방어("한 번 쓰면
     * 무효")가 경합에서 뚫리므로 하나의 스크립트로 묶는다.
     *
     * <p>세션 계보(familyId)로 두 경우를 구분한다:
     * <ul>
     *   <li><b>계보 다름</b> → 다른 기기 로그인이 이 계보를 밀어냈다. 정상적인 단일 세션 교체이므로
     *       세션을 폐기하지 않고 재로그인만 요구한다({@code 2}). 다중 기기 사용이 현재 세션까지
     *       강제 로그아웃시키던 문제를 막는다.</li>
     *   <li><b>계보 같고 해시 다름</b> → 같은 계보에서 이미 회전된 옛 토큰이 다시 왔다. 정상 클라이언트는
     *       회전 후 옛 토큰을 버리므로 <b>유출 정황</b>이다. 키를 지워 세션을 끊는다({@code -1}).
     *       (RFC 9700 — 재사용 감지 시 토큰 패밀리 무효화)</li>
     * </ul>
     *
     * <p>배포 전환 창에서 계보 도입 이전 형식({@code familyId:} 접두어 없는 값)이 남아 있을 수 있다.
     * 구분자가 없으면 회전할 수 없는 값이므로 {@code 0}(없음)으로 안전하게 강등해 재로그인을 유도한다 —
     * Lua 산술 오류로 500 이 나가는 것을 막는다. (정상 흐름은 옛 토큰에 계보 클레임이 없어 파싱 단계에서
     * 이미 걸러지므로 이 경로는 방어적 보루다.)
     *
     * <p>반환값: {@code 1} 회전 성공, {@code 0} 저장된 토큰 없음(만료·로그아웃·탈퇴 폐기·계보 이전 형식),
     * {@code -1} 재사용 감지(세션 폐기함), {@code 2} 다른 로그인에 밀려남(폐기 안 함).
     */
    private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>("""
            local stored = redis.call('GET', KEYS[1])
            if not stored then return 0 end
            local sep = string.find(stored, ':', 1, true)
            if not sep then return 0 end
            local storedFamily = string.sub(stored, 1, sep - 1)
            local storedHash = string.sub(stored, sep + 1)
            if storedFamily ~= ARGV[1] then return 2 end
            if storedHash ~= ARGV[2] then
              redis.call('DEL', KEYS[1])
              return -1
            end
            redis.call('SET', KEYS[1], ARGV[3], 'PX', tonumber(ARGV[4]))
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
        REUSE_DETECTED,
        /** 다른 기기 로그인이 이 계보를 밀어냈다. 정상 세션 교체이므로 폐기 없이 재로그인만 요구한다. */
        SUPERSEDED
    }

    public void save(Long userId, String familyId, String refreshToken, long ttlMs) {
        redisTemplate.opsForValue().set(
                key(userId), storedValue(familyId, refreshToken), Duration.ofMillis(ttlMs));
    }

    /**
     * 현재 Refresh Token 을 같은 계보의 새 토큰으로 원자적으로 교체한다.
     *
     * @param familyId            제시한 토큰의 세션 계보 (새 토큰도 같은 계보를 잇는다)
     * @param currentRefreshToken 클라이언트가 제시한 토큰 (원문)
     * @param newRefreshToken     교체할 새 토큰 (원문)
     * @param ttlMs               새 토큰의 유효 시간
     */
    public RotationResult rotate(Long userId, String familyId,
                                 String currentRefreshToken, String newRefreshToken, long ttlMs) {
        Long result = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(key(userId)),
                familyId,
                hash(currentRefreshToken),
                storedValue(familyId, newRefreshToken),
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
            case 2 -> RotationResult.SUPERSEDED;
            default -> RotationResult.NOT_FOUND;
        };
    }

    private String storedValue(String familyId, String refreshToken) {
        return familyId + VALUE_DELIMITER + hash(refreshToken);
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
