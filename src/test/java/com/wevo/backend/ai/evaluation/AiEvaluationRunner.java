package com.wevo.backend.ai.evaluation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.LongSupplier;

public final class AiEvaluationRunner {

    private final LongSupplier nanoTime;

    public AiEvaluationRunner() {
        this(System::nanoTime);
    }

    AiEvaluationRunner(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    public List<AiEvaluationSample> run(
            List<AiEvaluationFixture> fixtures,
            AiFixtureEvaluator evaluator,
            AiEvaluationBudget budget
    ) {
        if (fixtures == null || evaluator == null || budget == null) {
            throw new IllegalArgumentException("fixtures, evaluator, budget은 필수입니다.");
        }
        List<AiEvaluationFixture> orderedFixtures = fixtures.stream()
                .sorted(Comparator.comparing(fixture -> fixture.metadata().id()))
                .toList();
        List<AiEvaluationSample> samples = new ArrayList<>();
        int executedFixtures = 0;
        int providerRequests = 0;
        long outputTokens = 0;
        long elapsedMillis = 0;
        BigDecimal estimatedCost = BigDecimal.ZERO;
        boolean outputTokenMeasurementMissing = false;

        for (AiEvaluationFixture fixture : orderedFixtures) {
            if (budgetExceeded(
                    budget,
                    executedFixtures,
                    providerRequests,
                    outputTokens,
                    elapsedMillis,
                    estimatedCost,
                    outputTokenMeasurementMissing
            )) {
                samples.add(AiEvaluationSample.budgetExhausted(fixture.metadata().id()));
                continue;
            }

            long startedAt = nanoTime.getAsLong();
            AiEvaluationObservation observation = evaluator.evaluate(fixture);
            long finishedAt = nanoTime.getAsLong();
            long latencyMillis = Math.max(0L, (finishedAt - startedAt) / 1_000_000L);
            AiEvaluationSample sample = AiEvaluationSample.from(
                    fixture.metadata().id(), observation, latencyMillis
            );
            samples.add(sample);
            executedFixtures++;
            providerRequests += observation.attemptCount();
            elapsedMillis += latencyMillis;
            if (observation.usage() != null && observation.usage().outputTokens() != null) {
                outputTokens += observation.usage().outputTokens();
            } else if (observation.attemptCount() > 0) {
                outputTokenMeasurementMissing = true;
            }
            if (observation.cost() != null && observation.cost().estimatedCost() != null) {
                estimatedCost = estimatedCost.add(observation.cost().estimatedCost());
            }
        }
        return List.copyOf(samples);
    }

    private boolean budgetExceeded(
            AiEvaluationBudget budget,
            int executedFixtures,
            int providerRequests,
            long outputTokens,
            long elapsedMillis,
            BigDecimal estimatedCost,
            boolean outputTokenMeasurementMissing
    ) {
        if (executedFixtures >= budget.maxFixtures()
                || providerRequests >= budget.maxProviderRequests()
                || outputTokens >= budget.maxOutputTokens()
                || elapsedMillis >= budget.deadline().toMillis()
                || outputTokenMeasurementMissing) {
            return true;
        }
        return budget.maxEstimatedCost() != null
                && estimatedCost.compareTo(budget.maxEstimatedCost()) >= 0;
    }
}
