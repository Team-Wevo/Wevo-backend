package com.wevo.backend.ai.service;

import com.wevo.backend.ai.config.AiGuardrailProperties;
import com.wevo.backend.ai.config.AiPricingProperties;
import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.operations.AiOperationalMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiGuardrailServiceTest {

    @Test
    void redisFailureFailsClosedWithDedicatedSafeError() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList(), any(Object[].class)))
                .thenThrow(new IllegalStateException("redis://internal-host:6379 secret"));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiGuardrailService service = service(redis, pricing(), new BigDecimal("100"));
        service.setMetrics(new AiOperationalMetrics(registry));

        assertThatThrownBy(() -> service.reserve(command()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AI_GUARDRAIL_UNAVAILABLE);
                    assertThat(exception.getMessage()).doesNotContain("redis", "secret", "6379");
                });
        assertThat(registry.get(AiOperationalMetrics.GUARDRAIL_REJECTIONS)
                .tag("reason", "redis_fail_closed").counter().count()).isEqualTo(1);
    }

    @Test
    void missingPricingNeverTreatsEstimatedCostAsZero() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);

        assertThatThrownBy(() -> service(
                redis, new AiPricingProperties("test-v1", Map.of()), new BigDecimal("100"))
                .reserve(command()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.AI_PRICING_NOT_CONFIGURED));
    }

    @Test
    void conservativeEstimateIncludesStructuredAndTransportRetries() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);

        assertThatThrownBy(() -> service(redis, pricing(), new BigDecimal("0.000100"))
                .reserve(command()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.AI_PROJECT_COST_BUDGET_EXCEEDED));
    }

    private AiGuardrailService service(
            StringRedisTemplate redis,
            AiPricingProperties pricing,
            BigDecimal maxJobCost
    ) {
        Map<String, AiGuardrailProperties.FeatureLimits> features = Arrays.stream(AiFeature.values())
                .collect(Collectors.toMap(
                        AiFeature::configKey,
                        ignored -> new AiGuardrailProperties.FeatureLimits(100, 1_000, 2)));
        AiGuardrailProperties guardrails = new AiGuardrailProperties(
                true,
                "test-policy-v1",
                new AiGuardrailProperties.QuotaLimits(10, 100),
                new AiGuardrailProperties.QuotaLimits(100, 1_000),
                features,
                new AiGuardrailProperties.CostLimits(
                        maxJobCost.max(BigDecimal.ONE),
                        new BigDecimal("1000"),
                        maxJobCost));
        AiProperties ai = new AiProperties(
                "none",
                new AiProperties.ModelOptions(
                        "test-model", Duration.ofSeconds(1), 100, 10, 1000, 100,
                        AiProperties.ModelOptions.CONSERVATIVE_CHAR_V1,
                        AiProperties.ModelOptions.REJECT_OVERSIZED_INPUT_V1,
                        1, Duration.ZERO, Duration.ZERO),
                Map.of(),
                new AiProperties.StructuredOutputOptions(1));
        return new AiGuardrailService(
                redis,
                guardrails,
                ai,
                pricing,
                Clock.fixed(Instant.parse("2026-08-02T03:00:00Z"), ZoneOffset.UTC));
    }

    private AiPricingProperties pricing() {
        return new AiPricingProperties(
                "test-v1",
                Map.of("test-model", new AiPricingProperties.ModelPricing(
                        BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE)));
    }

    private AiGuardrailReservationCommand command() {
        return new AiGuardrailReservationCommand(
                "a".repeat(64), 1, 1L, 2L,
                AiFeature.DRAFT_GENERATION, "test-model", 10);
    }
}
