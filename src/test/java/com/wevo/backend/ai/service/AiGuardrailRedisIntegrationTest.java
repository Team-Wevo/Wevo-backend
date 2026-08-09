package com.wevo.backend.ai.service;

import com.wevo.backend.ai.config.AiGuardrailProperties;
import com.wevo.backend.ai.config.AiPricingProperties;
import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.domain.AiCostSnapshot;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.exception.AiGuardrailExceededException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("redis-integration")
@Testcontainers(disabledWithoutDocker = true)
class AiGuardrailRedisIntegrationTest {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;

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
        connectionFactory.getConnection().serverCommands().flushAll();
    }

    @Test
    void concurrentReservationsNeverExceedUserLimit() throws Exception {
        AiGuardrailService service = service(Clock.fixed(
                Instant.parse("2026-08-02T03:00:00Z"), ZoneOffset.UTC), 5);

        try (var executor = Executors.newFixedThreadPool(20)) {
            var tasks = IntStream.range(0, 20)
                    .<Callable<Boolean>>mapToObj(index -> () -> {
                        try {
                            service.reserve(command(UUID.randomUUID(), 1L, 7L));
                            return true;
                        } catch (AiGuardrailExceededException exception) {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(com.wevo.backend.global.exception.ErrorCode.AI_REQUEST_QUOTA_EXCEEDED);
                            return false;
                        }
                    }).toList();

            long accepted = executor.invokeAll(tasks).stream()
                    .filter(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new AssertionError(exception);
                        }
                    }).count();

            assertThat(accepted).isEqualTo(5);
        }
    }

    @Test
    void sameIdempotencyExecutionSharesOneReservation() {
        AiGuardrailService service = service(Clock.fixed(
                Instant.parse("2026-08-02T03:00:00Z"), ZoneOffset.UTC), 1);
        UUID identity = UUID.randomUUID();

        AiGuardrailReservation first = service.reserve(command(identity, 1L, 7L));
        AiGuardrailReservation duplicate = service.reserve(command(identity, 1L, 7L));
        service.rollback(duplicate);

        assertThat(first.active()).isTrue();
        assertThat(duplicate.active()).isFalse();
        assertThat(redisTemplate.opsForValue().get(first.counterKeys().getFirst()))
                .isEqualTo("1");
    }

    @Test
    void projectFeatureAndCostLimitsUseDistinctProductErrors() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-02T03:00:00Z"), ZoneOffset.UTC);
        AiGuardrailService projectLimited = service(
                clock, 100, 2, 100,
                new BigDecimal("100"), new BigDecimal("1000"), new BigDecimal("100"));
        projectLimited.reserve(command(UUID.randomUUID(), 1L, 1L));
        projectLimited.reserve(command(UUID.randomUUID(), 1L, 2L));
        assertThat(org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> projectLimited.reserve(command(UUID.randomUUID(), 1L, 3L)),
                AiGuardrailExceededException.class).getErrorCode())
                .isEqualTo(com.wevo.backend.global.exception.ErrorCode.AI_PROJECT_QUOTA_EXCEEDED);

        flush();
        AiGuardrailService featureLimited = service(
                clock, 100, 100, 2,
                new BigDecimal("100"), new BigDecimal("1000"), new BigDecimal("100"));
        featureLimited.reserve(command(UUID.randomUUID(), 1L, 1L));
        featureLimited.reserve(command(UUID.randomUUID(), 2L, 2L));
        assertThat(org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> featureLimited.reserve(command(UUID.randomUUID(), 3L, 3L)),
                AiGuardrailExceededException.class).getErrorCode())
                .isEqualTo(com.wevo.backend.global.exception.ErrorCode.AI_REQUEST_QUOTA_EXCEEDED);

        flush();
        AiGuardrailService costLimited = service(
                clock, 100, 100, 100,
                new BigDecimal("0.000200"), BigDecimal.ONE, new BigDecimal("0.000110"));
        costLimited.reserve(command(UUID.randomUUID(), 1L, 1L));
        assertThat(org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> costLimited.reserve(command(UUID.randomUUID(), 1L, 2L)),
                AiGuardrailExceededException.class).getErrorCode())
                .isEqualTo(com.wevo.backend.global.exception.ErrorCode.AI_PROJECT_COST_BUDGET_EXCEEDED);
    }

    @Test
    void kstDayBoundaryControlsRetryAfterAndKeyTtl() {
        AiGuardrailService service = service(Clock.fixed(
                Instant.parse("2026-08-02T14:59:30Z"), ZoneOffset.UTC), 1);
        AiGuardrailReservation first = service.reserve(command(UUID.randomUUID(), 1L, 7L));

        AiGuardrailExceededException failure = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> service.reserve(command(UUID.randomUUID(), 1L, 7L)),
                AiGuardrailExceededException.class);

        assertThat(failure.getRetryAfterSeconds()).isBetween(1L, 31L);
        assertThat(first.counterKeys()).anyMatch(key -> key.endsWith("day:20260802"));
        assertThat(redisTemplate.getExpire(first.counterKeys().get(1))).isPositive();
    }

    @Test
    void measuredUsageSettlesOnceAndUnmeasuredUsageKeepsReservation() {
        AiGuardrailService service = service(Clock.fixed(
                Instant.parse("2026-08-02T03:00:00Z"), ZoneOffset.UTC), 10);
        UUID measuredId = UUID.randomUUID();
        AiGuardrailReservation measured = service.reserve(command(measuredId, 1L, 7L));
        AiJob measuredJob = job(measuredId);
        service.markProviderStarted(measuredJob);
        service.recordUsage(measuredJob, UUID.randomUUID(), cost("0.000010"));
        service.recordUsage(measuredJob, UUID.randomUUID(), cost("0.000020"));
        service.settle(measuredJob);
        service.settle(measuredJob);

        assertThat(redisTemplate.opsForHash().get(measured.ledgerKey(), "state"))
                .isEqualTo("SETTLED");
        assertThat(redisTemplate.opsForValue().get(measured.counterKeys().get(6)))
                .isEqualTo("30");

        UUID unmeasuredId = UUID.randomUUID();
        AiGuardrailReservation unmeasured = service.reserve(command(unmeasuredId, 2L, 8L));
        AiJob unmeasuredJob = job(unmeasuredId);
        service.markProviderStarted(unmeasuredJob);
        service.recordUsage(unmeasuredJob, UUID.randomUUID(), AiCostSnapshot.unpriced("test-v1"));
        String reserved = redisTemplate.opsForHash().get(unmeasured.ledgerKey(), "reserved").toString();
        service.settle(unmeasuredJob);

        assertThat(redisTemplate.opsForHash().get(unmeasured.ledgerKey(), "state"))
                .isEqualTo("UNMEASURED");
        assertThat(redisTemplate.opsForValue().get(unmeasured.counterKeys().get(6)))
                .isEqualTo(reserved);
    }

    @Test
    void preProviderRollbackReleasesCostAndQuotaIdempotently() {
        AiGuardrailService service = service(Clock.fixed(
                Instant.parse("2026-08-02T03:00:00Z"), ZoneOffset.UTC), 10);
        AiGuardrailReservation reservation = service.reserve(command(UUID.randomUUID(), 1L, 7L));

        service.rollback(reservation);
        service.rollback(reservation);

        assertThat(redisTemplate.opsForHash().get(reservation.ledgerKey(), "state"))
                .isEqualTo("ROLLED_BACK");
        assertThat(redisTemplate.opsForValue().get(reservation.counterKeys().getFirst()))
                .isEqualTo("0");
        assertThat(redisTemplate.opsForValue().get(reservation.counterKeys().get(6)))
                .isEqualTo("0");
    }

    private AiGuardrailService service(Clock clock, int userPerMinute) {
        return service(
                clock, userPerMinute, 100, 100,
                new BigDecimal("100"), new BigDecimal("1000"), new BigDecimal("100"));
    }

    private AiGuardrailService service(
            Clock clock,
            int userPerMinute,
            int projectPerMinute,
            int featurePerMinute,
            BigDecimal dailyCost,
            BigDecimal monthlyCost,
            BigDecimal maxJobCost
    ) {
        Map<String, AiGuardrailProperties.FeatureLimits> features = Arrays.stream(AiFeature.values())
                .collect(Collectors.toMap(
                        AiFeature::configKey,
                        ignored -> new AiGuardrailProperties.FeatureLimits(featurePerMinute, 1_000, 1)));
        AiGuardrailProperties guardrails = new AiGuardrailProperties(
                true,
                "test-policy-v1",
                new AiGuardrailProperties.QuotaLimits(userPerMinute, 100),
                new AiGuardrailProperties.QuotaLimits(projectPerMinute, 1_000),
                features,
                new AiGuardrailProperties.CostLimits(
                        dailyCost, monthlyCost, maxJobCost));
        AiProperties ai = new AiProperties(
                "none",
                new AiProperties.ModelOptions(
                        "test-model", Duration.ofSeconds(1), 100, 10, 1000, 100,
                        AiProperties.ModelOptions.CONSERVATIVE_CHAR_V1,
                        AiProperties.ModelOptions.REJECT_OVERSIZED_INPUT_V1,
                        0, Duration.ZERO, Duration.ZERO),
                Map.of(),
                new AiProperties.StructuredOutputOptions(0));
        AiPricingProperties pricing = new AiPricingProperties(
                "test-v1",
                Map.of("test-model", new AiPricingProperties.ModelPricing(
                        BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE)));
        return new AiGuardrailService(redisTemplate, guardrails, ai, pricing, clock);
    }

    private AiGuardrailReservationCommand command(UUID requestId, Long projectId, Long userId) {
        return new AiGuardrailReservationCommand(
                hashSeed(requestId), 1, projectId, userId,
                AiFeature.DRAFT_GENERATION, "test-model", 10);
    }

    private AiJob job(UUID requestId) {
        AiJob job = mock(AiJob.class);
        when(job.getRequestId()).thenReturn(requestId);
        when(job.getIdempotencyKey()).thenReturn(hashSeed(requestId));
        when(job.getExecutionSequence()).thenReturn(1);
        return job;
    }

    private String hashSeed(UUID requestId) {
        return requestId.toString().replace("-", "").repeat(2);
    }

    private AiCostSnapshot cost(String usd) {
        return new AiCostSnapshot(
                "test-v1", BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, new BigDecimal(usd));
    }
}
