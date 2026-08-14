package com.wevo.backend.auth.service;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Refresh Token 회전의 원자성과 재사용 감지를 실제 Redis 로 검증한다. (#219, #221)
 *
 * <p>GET 비교 후 SET 하는 방식은 목이나 단위 테스트로는 정상처럼 보인다 — 두 명령 사이에 다른
 * 요청이 끼어드는 창은 실제 동시 실행에서만 드러난다. 그래서 Testcontainers 를 쓴다.
 * ({@code CLAUDE.md §8} — 동시성 검증은 실제 인프라로)
 */
@Tag("redis-integration")
@Testcontainers(disabledWithoutDocker = true)
class RefreshTokenRotationRedisIntegrationTest {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static final long TTL_MS = 60_000L;

    private static LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RefreshTokenService refreshTokenService;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
    }

    @AfterAll
    static void disconnect() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @BeforeEach
    void flush() {
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        try (var connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushAll();
        }
        refreshTokenService = new RefreshTokenService(redisTemplate);
    }

    @Test
    @DisplayName("같은 Refresh Token 으로 동시에 재발급하면 정확히 1건만 회전에 성공한다")
    void concurrentRotationSucceedsExactlyOnce() throws Exception {
        refreshTokenService.save(1L, "fam1", "current", TTL_MS);

        int attempts = 20;
        List<RefreshTokenService.RotationResult> results;
        try (var executor = Executors.newFixedThreadPool(attempts)) {
            var tasks = IntStream.range(0, attempts)
                    .<Callable<RefreshTokenService.RotationResult>>mapToObj(index ->
                            () -> refreshTokenService.rotate(1L, "fam1", "current", "next-" + index, TTL_MS))
                    .toList();
            results = executor.invokeAll(tasks).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new AssertionError(exception);
                        }
                    })
                    .toList();
        }

        assertThat(results).filteredOn(RefreshTokenService.RotationResult.ROTATED::equals)
                .as("회전은 한 번만 성공해야 한다 — 여러 건이 성공하면 같은 토큰으로 세션이 복제된다")
                .hasSize(1);

        // 나머지는 성공하지 않는다. 첫 실패가 재사용으로 걸려 키를 지우므로, 그 뒤에 도착한 요청은
        // 지워진 키를 보고 NOT_FOUND 가 된다 — 둘 다 "재발급 거부"라 결과는 같다.
        assertThat(results).filteredOn(result -> result != RefreshTokenService.RotationResult.ROTATED)
                .hasSize(attempts - 1)
                .containsAnyOf(RefreshTokenService.RotationResult.REUSE_DETECTED);
        assertThat(redisTemplate.hasKey("refresh_token:1"))
                .as("재사용이 감지되면 세션을 폐기한다")
                .isFalse();
    }

    @Test
    @DisplayName("회전에 성공하면 새 토큰으로 교체되고 옛 토큰은 더 이상 통하지 않는다")
    void rotationReplacesStoredToken() {
        refreshTokenService.save(2L, "fam2", "current", TTL_MS);

        assertThat(refreshTokenService.rotate(2L, "fam2", "current", "next", TTL_MS))
                .isEqualTo(RefreshTokenService.RotationResult.ROTATED);
        assertThat(refreshTokenService.rotate(2L, "fam2", "next", "third", TTL_MS))
                .isEqualTo(RefreshTokenService.RotationResult.ROTATED);
    }

    @Test
    @DisplayName("이미 회전된 옛 토큰을 다시 쓰면 재사용으로 보고 세션 전체를 폐기한다")
    void reusedTokenKillsSession() {
        refreshTokenService.save(3L, "fam3", "current", TTL_MS);
        refreshTokenService.rotate(3L, "fam3", "current", "next", TTL_MS);

        // 탈취자가 먼저 회전시킨 뒤 정상 사용자가 같은 계보의 옛 토큰을 내미는 상황.
        assertThat(refreshTokenService.rotate(3L, "fam3", "current", "another", TTL_MS))
                .isEqualTo(RefreshTokenService.RotationResult.REUSE_DETECTED);

        // 거부만 하면 탈취자의 새 토큰이 계속 살아 있다. 세션을 끊어 양쪽 다 재로그인시킨다.
        assertThat(redisTemplate.hasKey("refresh_token:3")).isFalse();
        assertThat(refreshTokenService.rotate(3L, "fam3", "next", "yet-another", TTL_MS))
                .isEqualTo(RefreshTokenService.RotationResult.NOT_FOUND);
    }

    @Test
    @DisplayName("저장된 토큰이 없으면 NOT_FOUND 를 돌려주고 키를 만들지 않는다")
    void missingTokenIsNotFound() {
        assertThat(refreshTokenService.rotate(4L, "fam4", "current", "next", TTL_MS))
                .isEqualTo(RefreshTokenService.RotationResult.NOT_FOUND);
        assertThat(redisTemplate.hasKey("refresh_token:4")).isFalse();
    }

    @Test
    @DisplayName("회전 시 새 토큰의 유효 기간이 다시 설정된다")
    void rotationRefreshesTtl() {
        refreshTokenService.save(5L, "fam5", "current", TTL_MS);
        refreshTokenService.rotate(5L, "fam5", "current", "next", TTL_MS);

        Long ttl = redisTemplate.getExpire("refresh_token:5", java.util.concurrent.TimeUnit.MILLISECONDS);
        assertThat(ttl).isNotNull().isPositive().isLessThanOrEqualTo(TTL_MS);
    }

    @Test
    @DisplayName("다른 기기 로그인이 계보를 밀어내도 현재 세션은 살아 있고, 밀려난 토큰만 재로그인을 요구한다 (#290)")
    void supersededByNewLoginDoesNotKillSession() {
        // 기기 A 로그인 → 기기 B 로그인(같은 사용자, 새 계보로 덮어씀).
        refreshTokenService.save(6L, "famA", "tokenA", TTL_MS);
        refreshTokenService.save(6L, "famB", "tokenB", TTL_MS);

        // 기기 A 가 옛 계보 토큰으로 재발급 시도 → 밀려남(SUPERSEDED). 세션은 폐기하지 않는다.
        assertThat(refreshTokenService.rotate(6L, "famA", "tokenA", "tokenA2", TTL_MS))
                .isEqualTo(RefreshTokenService.RotationResult.SUPERSEDED);
        assertThat(redisTemplate.hasKey("refresh_token:6"))
                .as("밀려난 토큰은 현재(기기 B) 세션을 폐기하지 않는다")
                .isTrue();

        // 현재 세션(기기 B)은 정상 회전된다 — 다중 기기 로그인이 현재 세션을 강제 로그아웃시키지 않는다.
        assertThat(refreshTokenService.rotate(6L, "famB", "tokenB", "tokenB2", TTL_MS))
                .isEqualTo(RefreshTokenService.RotationResult.ROTATED);
    }

    @Test
    @DisplayName("계보 도입 이전 형식(구분자 없는 레거시 값)으로 회전해도 500 없이 NOT_FOUND 로 강등한다 (#290)")
    void legacyValueWithoutFamilyDelimiterDegradesToNotFound() {
        // 배포 전환 창에 남은 계보 도입 이전 저장값(hash 만, familyId: 접두어 없음)을 직접 넣는다.
        redisTemplate.opsForValue().set("refresh_token:7", "legacy-hash-without-delimiter");

        // Lua string.find 가 nil 을 반환해도 산술 오류(500)로 터지지 않고 NOT_FOUND 로 강등해야 한다.
        assertThat(refreshTokenService.rotate(7L, "famX", "any-token", "next", TTL_MS))
                .isEqualTo(RefreshTokenService.RotationResult.NOT_FOUND);
    }
}
