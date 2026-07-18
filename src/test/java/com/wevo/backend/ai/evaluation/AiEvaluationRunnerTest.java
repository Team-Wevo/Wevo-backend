package com.wevo.backend.ai.evaluation;

import com.wevo.backend.ai.client.AiUsageMetadata;
import com.wevo.backend.ai.domain.AiCostSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class AiEvaluationRunnerTest {

    private final AiEvaluationFixtureLoader loader = new AiEvaluationFixtureLoader();
    private final List<AiEvaluationFixture> fixtures = loader.loadDataset(
            "ai/evaluation/issue-detection/dataset-v1.index.json"
    ).subList(0, 3);

    @Test
    void stopsAdditionalCallsWhenFixtureOrRequestBudgetIsReached() {
        assertStopsAfterFirst(new AiEvaluationBudget(
                1, 10, 100, Duration.ofSeconds(1), null
        ), success(1, 1L, "0.001"), 1);
        assertStopsAfterFirst(new AiEvaluationBudget(
                10, 2, 100, Duration.ofSeconds(1), null
        ), success(2, 1L, "0.001"), 1);
    }

    @Test
    void stopsAdditionalCallsWhenTokenDeadlineOrPricedCostBudgetIsReached() {
        assertStopsAfterFirst(new AiEvaluationBudget(
                10, 10, 5, Duration.ofSeconds(1), null
        ), success(1, 5L, "0.001"), 1);
        assertStopsAfterFirst(new AiEvaluationBudget(
                10, 10, 100, Duration.ofSeconds(1), null
        ), success(1, null, "0.001"), 1);
        assertStopsAfterFirst(new AiEvaluationBudget(
                10, 10, 100, Duration.ofMillis(10), null
        ), success(1, 1L, "0.001"), 10);
        assertStopsAfterFirst(new AiEvaluationBudget(
                10, 10, 100, Duration.ofSeconds(1), new BigDecimal("0.01")
        ), success(1, 1L, "0.01"), 1);
    }

    private void assertStopsAfterFirst(
            AiEvaluationBudget budget,
            AiEvaluationObservation observation,
            long latencyMillis
    ) {
        AtomicInteger calls = new AtomicInteger();
        LongSupplier nanoTime = alternatingNanoTime(latencyMillis);
        AiEvaluationRunner runner = new AiEvaluationRunner(nanoTime);

        List<AiEvaluationSample> samples = runner.run(fixtures, fixture -> {
            calls.incrementAndGet();
            return observation;
        }, budget);

        assertThat(calls).hasValue(1);
        assertThat(samples).hasSize(3);
        assertThat(samples).extracting(AiEvaluationSample::outcome)
                .containsExactly(
                        AiEvaluationOutcome.SUCCESS,
                        AiEvaluationOutcome.BUDGET_EXHAUSTED,
                        AiEvaluationOutcome.BUDGET_EXHAUSTED
                );
    }

    private LongSupplier alternatingNanoTime(long latencyMillis) {
        AtomicInteger calls = new AtomicInteger();
        return () -> calls.getAndIncrement() % 2 == 0 ? 0L : latencyMillis * 1_000_000L;
    }

    private AiEvaluationObservation success(int attempts, Long outputTokens, String cost) {
        return AiEvaluationObservation.success(
                AiEvaluationCandidate.empty(),
                new AiUsageMetadata("nvidia", "request", "model", 1L, outputTokens, null, null),
                new AiCostSnapshot(
                        "pricing-v1",
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        new BigDecimal(cost)
                ),
                attempts
        );
    }
}
