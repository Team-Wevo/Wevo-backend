package com.wevo.backend.ai.operations;

import com.wevo.backend.ai.client.AiUsageMetadata;
import com.wevo.backend.ai.domain.AiCostSnapshot;
import com.wevo.backend.ai.domain.AiErrorType;
import com.wevo.backend.ai.domain.AiFeature;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 원문이나 요청 식별자를 tag로 사용하지 않는 AI 저카디널리티 metric 파사드. */
@Component
public class AiOperationalMetrics {

    public static final String REQUESTS = "wevo.ai.requests";
    public static final String PROVIDER_LATENCY = "wevo.ai.provider.latency";
    public static final String END_TO_END_LATENCY = "wevo.ai.end_to_end.latency";
    public static final String QUEUE_LATENCY = "wevo.ai.queue.latency";
    public static final String TOKENS = "wevo.ai.tokens";
    public static final String COST = "wevo.ai.cost.usd";
    public static final String ATTEMPTS = "wevo.ai.attempts";
    public static final String TRANSPORT_RETRIES = "wevo.ai.transport.retries";
    public static final String CORRECTION_RETRIES = "wevo.ai.correction.retries";
    public static final String SCHEMA_RESULTS = "wevo.ai.schema.results";
    public static final String JOB_EVENTS = "wevo.ai.job.events";
    public static final String GUARDRAIL_REJECTIONS = "wevo.ai.guardrail.rejections";
    public static final String BUDGET_UTILIZATION = "wevo.ai.guardrail.budget.utilization";

    public static final Set<String> ALLOWED_TAG_KEYS = Set.of(
            "feature", "provider", "model_family", "prompt_version", "schema_version",
            "outcome", "error_type", "token_type", "event", "reason", "status"
    );

    private static final Pattern GPT_FAMILY = Pattern.compile("^(gpt-[0-9]+(?:\\.[0-9]+)?)");
    private final MeterRegistry registry;

    public AiOperationalMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordSuccess(
            AiFeature feature,
            String provider,
            String model,
            String promptVersion,
            String schemaVersion,
            Duration endToEnd,
            int attempts,
            AiUsageMetadata usage,
            AiCostSnapshot cost,
            boolean structured
    ) {
        List<Tag> tags = invocationTags(feature, provider, model, promptVersion, schemaVersion,
                "success", "none");
        Counter.builder(REQUESTS).tags(tags).register(registry).increment();
        Timer.builder(END_TO_END_LATENCY).publishPercentileHistogram()
                .tags(tags).register(registry).record(nonNegative(endToEnd));
        DistributionSummary.builder(ATTEMPTS).tags(tags).register(registry).record(attempts);
        if (structured) {
            Counter.builder(SCHEMA_RESULTS).tag("feature", feature.configKey())
                    .tag("model_family", modelFamily(model)).tag("outcome", "valid")
                    .register(registry).increment();
        }
        recordUsage(feature, provider, model, usage, cost);
    }

    public void recordFailure(
            AiFeature feature,
            String provider,
            String model,
            String promptVersion,
            String schemaVersion,
            Duration endToEnd,
            Integer attempts,
            AiErrorType errorType,
            AiUsageMetadata usage,
            AiCostSnapshot cost,
            boolean structured
    ) {
        String normalizedError = errorType == null ? "internal_error" : errorType.name().toLowerCase(Locale.ROOT);
        List<Tag> tags = invocationTags(feature, provider, model, promptVersion, schemaVersion,
                "failure", normalizedError);
        Counter.builder(REQUESTS).tags(tags).register(registry).increment();
        Timer.builder(END_TO_END_LATENCY).publishPercentileHistogram()
                .tags(tags).register(registry).record(nonNegative(endToEnd));
        if (attempts != null) {
            DistributionSummary.builder(ATTEMPTS).tags(tags).register(registry).record(attempts);
        }
        if (structured && isSchemaFailure(errorType)) {
            Counter.builder(SCHEMA_RESULTS).tag("feature", feature.configKey())
                    .tag("model_family", modelFamily(model)).tag("outcome", "invalid")
                    .register(registry).increment();
        }
        recordUsage(feature, provider, model, usage, cost);
    }

    public void recordProviderLatency(AiFeature feature, String provider, String model, Duration latency) {
        Timer.builder(PROVIDER_LATENCY).publishPercentileHistogram()
                .tag("feature", feature.configKey())
                .tag("provider", safeProvider(provider))
                .tag("model_family", modelFamily(model))
                .register(registry).record(nonNegative(latency));
    }

    public void recordRetries(
            AiFeature feature,
            String provider,
            String model,
            int transportRetries,
            int correctionRetries
    ) {
        List<Tag> tags = List.of(
                Tag.of("feature", feature.configKey()),
                Tag.of("provider", safeProvider(provider)),
                Tag.of("model_family", modelFamily(model)));
        Counter.builder(TRANSPORT_RETRIES).tags(tags).register(registry)
                .increment(Math.max(0, transportRetries));
        Counter.builder(CORRECTION_RETRIES).tags(tags).register(registry)
                .increment(Math.max(0, correctionRetries));
    }

    public void recordQueueLatency(AiFeature feature, String model, Duration latency) {
        Timer.builder(QUEUE_LATENCY).publishPercentileHistogram()
                .tag("feature", feature.configKey())
                .tag("model_family", modelFamily(model))
                .register(registry).record(nonNegative(latency));
    }

    public void recordJobEvent(AiFeature feature, String event) {
        Counter.builder(JOB_EVENTS)
                .tag("feature", feature == null ? "unknown" : feature.configKey())
                .tag("event", normalizeValue(event))
                .register(registry).increment();
    }

    public void recordGuardrailRejection(AiFeature feature, String reason) {
        Counter.builder(GUARDRAIL_REJECTIONS)
                .tag("feature", feature == null ? "unknown" : feature.configKey())
                .tag("reason", normalizeValue(reason))
                .register(registry).increment();
    }

    public void recordBudgetUtilization(double ratio) {
        DistributionSummary.builder(BUDGET_UTILIZATION)
                .baseUnit("ratio")
                .register(registry).record(Math.max(0.0d, Math.min(1.0d, ratio)));
    }

    public String modelFamily(String model) {
        if (model == null || model.isBlank()) {
            return "unknown";
        }
        String normalized = model.toLowerCase(Locale.ROOT);
        Matcher gpt = GPT_FAMILY.matcher(normalized);
        if (gpt.find()) {
            return gpt.group(1);
        }
        if (normalized.contains("mistral")) {
            return "mistral";
        }
        if (normalized.contains("claude")) {
            return "claude";
        }
        return "other";
    }

    private void recordUsage(
            AiFeature feature,
            String provider,
            String model,
            AiUsageMetadata usage,
            AiCostSnapshot cost
    ) {
        if (usage != null) {
            recordTokens(feature, provider, model, "input", usage.inputTokens());
            recordTokens(feature, provider, model, "output", usage.outputTokens());
            recordTokens(feature, provider, model, "reasoning", usage.reasoningTokens());
            recordTokens(feature, provider, model, "cache_read", usage.cacheReadInputTokens());
            recordTokens(feature, provider, model, "cache_write", usage.cacheWriteInputTokens());
        }
        BigDecimal estimatedCost = cost == null ? null : cost.estimatedCost();
        if (estimatedCost != null) {
            DistributionSummary.builder(COST)
                    .baseUnit("USD")
                    .tag("feature", feature.configKey())
                    .tag("provider", safeProvider(provider))
                    .tag("model_family", modelFamily(model))
                    .register(registry).record(estimatedCost.doubleValue());
        }
    }

    private void recordTokens(
            AiFeature feature,
            String provider,
            String model,
            String tokenType,
            Long value
    ) {
        if (value == null) {
            return;
        }
        DistributionSummary.builder(TOKENS)
                .baseUnit("tokens")
                .tag("feature", feature.configKey())
                .tag("provider", safeProvider(provider))
                .tag("model_family", modelFamily(model))
                .tag("token_type", tokenType)
                .register(registry).record(value);
    }

    private List<Tag> invocationTags(
            AiFeature feature,
            String provider,
            String model,
            String promptVersion,
            String schemaVersion,
            String outcome,
            String errorType
    ) {
        return List.of(
                Tag.of("feature", feature.configKey()),
                Tag.of("provider", safeProvider(provider)),
                Tag.of("model_family", modelFamily(model)),
                Tag.of("prompt_version", normalizeVersion(promptVersion)),
                Tag.of("schema_version", normalizeVersion(schemaVersion)),
                Tag.of("outcome", outcome),
                Tag.of("error_type", errorType)
        );
    }

    private boolean isSchemaFailure(AiErrorType type) {
        return type == AiErrorType.JSON_PARSE_FAILED
                || type == AiErrorType.SCHEMA_VALIDATION_FAILED
                || type == AiErrorType.TYPE_CONVERSION_FAILED
                || type == AiErrorType.SEMANTIC_VALIDATION_FAILED
                || type == AiErrorType.INVALID_RESPONSE;
    }

    private Duration nonNegative(Duration duration) {
        return duration == null || duration.isNegative() ? Duration.ZERO : duration;
    }

    private String safeProvider(String provider) {
        return provider != null && provider.matches("[a-z][a-z0-9-]{0,29}") ? provider : "unknown";
    }

    private String normalizeVersion(String value) {
        return value != null && value.matches("[a-z0-9][a-z0-9_.:-]{0,99}") ? value : "unknown";
    }

    private String normalizeValue(String value) {
        return value == null ? "unknown" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
    }
}
