package com.wevo.backend.ai.operations;

import com.wevo.backend.ai.client.AiUsageMetadata;
import com.wevo.backend.ai.domain.AiCostSnapshot;
import com.wevo.backend.ai.domain.AiErrorType;
import com.wevo.backend.ai.domain.AiFeature;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AiOperationalMetricsTest {

    @Test
    void recordsSuccessTokensCostAndSchemaWithoutHighCardinalityTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiOperationalMetrics metrics = new AiOperationalMetrics(registry);
        AiUsageMetadata usage = new AiUsageMetadata(
                "openai", "provider-request-sensitive", "gpt-5.6-luna-2026-07-31",
                10L, 4L, 3L, 2L, 1L);
        AiCostSnapshot cost = new AiCostSnapshot(
                "pricing-v1", BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("0.001"));

        metrics.recordSuccess(
                AiFeature.DRAFT_GENERATION, "openai", usage.modelId(),
                "draft-generation:v2", "draft-generation:v1", Duration.ofSeconds(2),
                2, usage, cost, true);
        metrics.recordRetries(AiFeature.DRAFT_GENERATION, "openai", usage.modelId(), 1, 2);

        assertThat(registry.get(AiOperationalMetrics.REQUESTS)
                .tag("outcome", "success").counter().count()).isEqualTo(1);
        assertThat(registry.get(AiOperationalMetrics.SCHEMA_RESULTS)
                .tag("outcome", "valid").counter().count()).isEqualTo(1);
        assertThat(registry.get(AiOperationalMetrics.TOKENS).summaries().stream()
                .mapToDouble(summary -> summary.totalAmount()).sum()).isEqualTo(20);
        assertThat(registry.get(AiOperationalMetrics.COST).summary().totalAmount()).isEqualTo(0.001);
        assertThat(registry.get(AiOperationalMetrics.TRANSPORT_RETRIES).counter().count()).isEqualTo(1);
        assertThat(registry.get(AiOperationalMetrics.CORRECTION_RETRIES).counter().count()).isEqualTo(2);
        assertThat(registry.getMeters()).allSatisfy(meter -> {
            assertThat(meter.getId().getTags())
                    .allSatisfy(tag -> assertThat(AiOperationalMetrics.ALLOWED_TAG_KEYS)
                            .contains(tag.getKey()));
            assertThat(meter.getId().getTags())
                    .noneSatisfy(tag -> assertThat(tag.getValue()).contains(
                            "provider-request-sensitive", "project", "user@example.com"));
        });
    }

    @Test
    void normalizesSchemaFailureAndUnknownModel() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiOperationalMetrics metrics = new AiOperationalMetrics(registry);

        metrics.recordFailure(
                AiFeature.DRAFT_REVIEW, "openai", "tenant-generated-model-12345",
                "draft-review:v1", "draft-review:v1", Duration.ofMillis(50), 3,
                AiErrorType.SCHEMA_VALIDATION_FAILED, null, AiCostSnapshot.unpriced("v1"), true);
        metrics.recordJobEvent(AiFeature.DRAFT_REVIEW, "stale_discarded");
        metrics.recordJobEvent(null, "orphan_recovered");
        metrics.recordGuardrailRejection(AiFeature.DRAFT_REVIEW, "redis_fail_closed");

        assertThat(registry.get(AiOperationalMetrics.REQUESTS)
                .tag("model_family", "other")
                .tag("error_type", "schema_validation_failed").counter().count()).isEqualTo(1);
        assertThat(registry.get(AiOperationalMetrics.SCHEMA_RESULTS)
                .tag("outcome", "invalid").counter().count()).isEqualTo(1);
        assertThat(registry.get(AiOperationalMetrics.JOB_EVENTS).counters())
                .extracting(counter -> counter.getId().getTag("event"))
                .containsExactlyInAnyOrder("stale_discarded", "orphan_recovered");
        assertThat(registry.get(AiOperationalMetrics.GUARDRAIL_REJECTIONS)
                .tag("reason", "redis_fail_closed").counter().count()).isEqualTo(1);
    }
}
