package com.wevo.backend.ai.service;

import com.wevo.backend.ai.config.AiGuardrailProperties;
import com.wevo.backend.ai.config.AiPricingProperties;
import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.domain.AiCostSnapshot;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.exception.AiGuardrailExceededException;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.wevo.backend.ai.operations.AiOperationalMetrics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Redis 원자 연산으로 AI 요청 quota와 프로젝트 비용을 예약·정산한다.
 *
 * <p>비용은 Redis 정수 연산을 위해 micro USD로 올림 변환한다. usage 또는 가격이 하나라도
 * 측정되지 않으면 예약액을 유지해 0원 우회를 막는다.</p>
 */
@Service
public class AiGuardrailService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final BigDecimal MICRO_USD = BigDecimal.valueOf(1_000_000L);
    private static final BigDecimal ONE_MILLION_TOKENS = BigDecimal.valueOf(1_000_000L);
    private static final DateTimeFormatter MINUTE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    private static final DefaultRedisScript<List> RESERVE_SCRIPT = script("""
            if redis.call('EXISTS', KEYS[1]) == 1 then
              if redis.call('HGET', KEYS[1], 'state') == 'ROLLED_BACK' then
                redis.call('DEL', KEYS[1])
              else
                return {11, 0}
              end
            end
            for i = 2, 7 do
              local current = tonumber(redis.call('GET', KEYS[i]) or '0')
              local limit = tonumber(ARGV[i - 1])
              if current + 1 > limit then
                local code = 1
                if i == 4 or i == 5 then code = 2 elseif i == 6 or i == 7 then code = 3 end
                local retry = tonumber(ARGV[16])
                if i == 3 or i == 5 or i == 7 then retry = tonumber(ARGV[17]) end
                return {code, retry}
              end
            end
            local estimated = tonumber(ARGV[7])
            local daily = tonumber(redis.call('GET', KEYS[8]) or '0')
            if daily + estimated > tonumber(ARGV[8]) then return {4, tonumber(ARGV[17])} end
            local monthly = tonumber(redis.call('GET', KEYS[9]) or '0')
            if monthly + estimated > tonumber(ARGV[9]) then return {4, tonumber(ARGV[18])} end
            for i = 2, 7 do
              redis.call('INCR', KEYS[i])
              redis.call('PEXPIRE', KEYS[i], tonumber(ARGV[8 + i]))
            end
            redis.call('INCRBY', KEYS[8], estimated)
            redis.call('PEXPIRE', KEYS[8], tonumber(ARGV[19]))
            redis.call('INCRBY', KEYS[9], estimated)
            redis.call('PEXPIRE', KEYS[9], tonumber(ARGV[20]))
            redis.call('HSET', KEYS[1],
              'state', 'RESERVED', 'reserved', estimated, 'actual', 0, 'actual_count', 0,
              'provider_started', 0, 'unmeasured', 0, 'day_key', KEYS[8], 'month_key', KEYS[9])
            redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[21]))
            return {10, daily + estimated}
            """);

    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT = scriptLong("""
            if redis.call('HGET', KEYS[1], 'state') ~= 'RESERVED' then return 0 end
            for i = 2, 7 do
              if redis.call('EXISTS', KEYS[i]) == 1 then
                local value = tonumber(redis.call('DECR', KEYS[i]) or '0')
                if value < 0 then redis.call('SET', KEYS[i], 0) end
              end
            end
            local reserved = tonumber(redis.call('HGET', KEYS[1], 'reserved') or '0')
            for i = 8, 9 do
              if redis.call('EXISTS', KEYS[i]) == 1 then
                local value = tonumber(redis.call('INCRBY', KEYS[i], -reserved) or '0')
                if value < 0 then redis.call('SET', KEYS[i], 0) end
              end
            end
            redis.call('HSET', KEYS[1], 'state', 'ROLLED_BACK')
            redis.call('PEXPIRE', KEYS[1], 3600000)
            return 1
            """);

    private static final DefaultRedisScript<Long> START_SCRIPT = scriptLong("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return -1 end
            local state = redis.call('HGET', KEYS[1], 'state')
            if state ~= 'RESERVED' then return 0 end
            redis.call('HSET', KEYS[1], 'provider_started', 1)
            return 1
            """);

    private static final DefaultRedisScript<Long> RECORD_USAGE_SCRIPT = scriptLong("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return -1 end
            local state = redis.call('HGET', KEYS[1], 'state')
            if state ~= 'RESERVED' then return 0 end
            if redis.call('HSETNX', KEYS[1], ARGV[1], ARGV[2]) == 0 then return 2 end
            if ARGV[2] == 'UNMEASURED' then
              redis.call('HSET', KEYS[1], 'unmeasured', 1)
            else
              redis.call('HINCRBY', KEYS[1], 'actual', tonumber(ARGV[2]))
              redis.call('HINCRBY', KEYS[1], 'actual_count', 1)
            end
            return 1
            """);

    private static final DefaultRedisScript<Long> SETTLE_SCRIPT = scriptLong("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return -1 end
            local state = redis.call('HGET', KEYS[1], 'state')
            if state ~= 'RESERVED' then return 0 end
            local reserved = tonumber(redis.call('HGET', KEYS[1], 'reserved') or '0')
            if redis.call('HGET', KEYS[1], 'provider_started') ~= '1' then
              for i = 2, 3 do
                if redis.call('EXISTS', KEYS[i]) == 1 then
                  local value = tonumber(redis.call('INCRBY', KEYS[i], -reserved) or '0')
                  if value < 0 then redis.call('SET', KEYS[i], 0) end
                end
              end
              redis.call('HSET', KEYS[1], 'state', 'RELEASED')
              return 1
            end
            if redis.call('HGET', KEYS[1], 'unmeasured') == '1'
                or tonumber(redis.call('HGET', KEYS[1], 'actual_count') or '0') == 0 then
              redis.call('HSET', KEYS[1], 'state', 'UNMEASURED')
              return 2
            end
            local actual = tonumber(redis.call('HGET', KEYS[1], 'actual') or '0')
            local delta = actual - reserved
            if redis.call('EXISTS', KEYS[2]) == 1 then redis.call('INCRBY', KEYS[2], delta) end
            if redis.call('EXISTS', KEYS[3]) == 1 then redis.call('INCRBY', KEYS[3], delta) end
            redis.call('HSET', KEYS[1], 'state', 'SETTLED')
            return 1
            """);

    private final StringRedisTemplate redisTemplate;
    private final AiGuardrailProperties properties;
    private final AiProperties aiProperties;
    private final AiPricingProperties pricingProperties;
    private final Clock clock;
    private AiOperationalMetrics metrics;

    public AiGuardrailService(
            StringRedisTemplate redisTemplate,
            AiGuardrailProperties properties,
            AiProperties aiProperties,
            AiPricingProperties pricingProperties,
            Clock clock
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.aiProperties = aiProperties;
        this.pricingProperties = pricingProperties;
        this.clock = clock;
    }

    public AiGuardrailReservation reserve(AiGuardrailReservationCommand command) {
        Objects.requireNonNull(command, "command는 필수입니다.");
        try {
            return reserveInternal(command);
        } catch (BusinessException exception) {
            if (metrics != null) {
                metrics.recordGuardrailRejection(command.feature(), guardrailReason(exception));
            }
            throw exception;
        }
    }

    private AiGuardrailReservation reserveInternal(AiGuardrailReservationCommand command) {
        if (!properties.isEnabled()) {
            return AiGuardrailReservation.disabled();
        }

        Window window = window();
        long estimatedMicros = estimateMicros(command);
        long maxJobMicros = toMicros(properties.cost().maxEstimatedJobCostUsd());
        if (estimatedMicros > maxJobMicros) {
            throw exceeded(ErrorCode.AI_PROJECT_COST_BUDGET_EXCEEDED, window.secondsUntilDay());
        }

        List<String> keys = keys(command, window);
        AiGuardrailProperties.FeatureLimits feature = properties.limitsFor(command.feature());
        List<String> args = List.of(
                properties.user().perMinute().toString(),
                properties.user().perDay().toString(),
                properties.project().perMinute().toString(),
                properties.project().perDay().toString(),
                feature.perMinute().toString(),
                feature.perDay().toString(),
                Long.toString(estimatedMicros),
                Long.toString(toMicros(properties.cost().dailyBudgetUsd())),
                Long.toString(toMicros(properties.cost().monthlyBudgetUsd())),
                Long.toString(window.minuteTtlMs()),
                Long.toString(window.dayTtlMs()),
                Long.toString(window.minuteTtlMs()),
                Long.toString(window.dayTtlMs()),
                Long.toString(window.minuteTtlMs()),
                Long.toString(window.dayTtlMs()),
                Long.toString(window.secondsUntilMinute()),
                Long.toString(window.secondsUntilDay()),
                Long.toString(window.secondsUntilMonth()),
                Long.toString(window.dayTtlMs()),
                Long.toString(window.monthTtlMs()),
                Long.toString(window.ledgerTtlMs())
        );

        List<?> result = execute(RESERVE_SCRIPT, keys, args);
        if (result == null || result.size() < 2) {
            throw unavailable();
        }
        long code = ((Number) result.get(0)).longValue();
        long retryAfter = Math.max(1L, ((Number) result.get(1)).longValue());
        if (code == 1 || code == 3) {
            throw exceeded(ErrorCode.AI_REQUEST_QUOTA_EXCEEDED, retryAfter);
        }
        if (code == 2) {
            throw exceeded(ErrorCode.AI_PROJECT_QUOTA_EXCEEDED, retryAfter);
        }
        if (code == 4) {
            throw exceeded(ErrorCode.AI_PROJECT_COST_BUDGET_EXCEEDED, retryAfter);
        }
        if (code == 11) {
            throw new LedgerReservationConflictException();
        }
        if (code != 10) {
            throw unavailable();
        }
        if (metrics != null) {
            metrics.recordBudgetUtilization(
                    ((Number) result.get(1)).doubleValue()
                            / Math.max(1.0d, toMicros(properties.cost().dailyBudgetUsd())));
        }
        return new AiGuardrailReservation(true, keys.getFirst(), keys.subList(1, keys.size()));
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public void rollback(AiGuardrailReservation reservation) {
        if (reservation == null || !reservation.active()) {
            return;
        }
        List<String> keys = new ArrayList<>();
        keys.add(reservation.ledgerKey());
        keys.addAll(reservation.counterKeys());
        execute(ROLLBACK_SCRIPT, keys, List.of());
    }

    public void markProviderStarted(AiJob job) {
        if (!properties.isEnabled() || job == null) {
            return;
        }
        Long result = execute(START_SCRIPT, List.of(ledgerKey(job)), List.of());
        if (result == null || result < 0 || result > 1) {
            throw unavailable();
        }
        recordLifecycleStateMismatch(job, "provider_start", result);
    }

    public void recordUsage(AiJob job, UUID usageRequestId, AiCostSnapshot cost) {
        if (!properties.isEnabled() || job == null) {
            return;
        }
        String value = cost == null || cost.estimatedCost() == null
                ? "UNMEASURED"
                : Long.toString(toMicros(cost.estimatedCost()));
        String usageField = "usage:" + hash(usageRequestId.toString());
        Long result;
        try {
            result = execute(
                    RECORD_USAGE_SCRIPT,
                    List.of(ledgerKey(job)),
                    List.of(usageField, value)
            );
        } catch (RuntimeException exception) {
            if (metrics != null) {
                metrics.recordGuardrailRejection(job.getFeature(), "usage_record_error");
            }
            throw exception;
        }
        if (result == null || result < 0 || result > 2) {
            throw unavailable();
        }
        recordLifecycleStateMismatch(job, "usage_record", result);
    }

    /** 성공·실패·stale 모두 동일하게 실제 누적 비용으로 멱등 정산한다. */
    public void settle(AiJob job) {
        if (!properties.isEnabled()) {
            return;
        }
        Objects.requireNonNull(job, "job은 필수입니다.");
        String ledgerKey = ledgerKey(job);
        List<Object> budgetKeys;
        try {
            budgetKeys = redisTemplate.opsForHash().multiGet(
                    ledgerKey, List.of("day_key", "month_key"));
        } catch (RuntimeException exception) {
            if (metrics != null) {
                metrics.recordGuardrailRejection(job.getFeature(), "settlement_error");
            }
            throw unavailable();
        }
        if (budgetKeys == null || budgetKeys.size() != 2
                || budgetKeys.get(0) == null || budgetKeys.get(1) == null) {
            throw unavailable();
        }
        Long result = execute(
                SETTLE_SCRIPT,
                List.of(ledgerKey, budgetKeys.get(0).toString(), budgetKeys.get(1).toString()),
                List.of()
        );
        if (result == null || result < 0 || result > 2) {
            throw unavailable();
        }
        recordLifecycleStateMismatch(job, "settlement", result);
    }

    private long estimateMicros(AiGuardrailReservationCommand command) {
        AiPricingProperties.ModelPricing pricing = pricingProperties.models().get(command.modelId());
        if (pricing == null || pricingProperties.version() == null) {
            throw new BusinessException(ErrorCode.AI_PRICING_NOT_CONFIGURED);
        }
        AiProperties.ModelOptions options = aiProperties.optionsFor(command.feature())
                .withExecutionSnapshot(command.modelId(), command.maxOutputTokens());

        BigDecimal inputRate = pricing.inputPerMillionTokens()
                .max(pricing.cacheReadPerMillionTokens())
                .max(pricing.cacheWritePerMillionTokens());
        BigDecimal oneAttempt = BigDecimal.valueOf(options.maxInputTokens()).multiply(inputRate)
                .add(BigDecimal.valueOf(options.maxOutputTokens())
                        .multiply(pricing.outputPerMillionTokens()))
                .divide(ONE_MILLION_TOKENS, 12, RoundingMode.CEILING);
        long attemptsPerInvocation = Math.multiplyExact(
                (long) options.maxRetries() + 1L,
                (long) aiProperties.structuredOutput().maxCorrectionRetries() + 1L);
        long maximumAttempts = Math.multiplyExact(
                attemptsPerInvocation,
                properties.limitsFor(command.feature()).maxProviderRequests().longValue());
        return toMicros(oneAttempt.multiply(BigDecimal.valueOf(maximumAttempts)));
    }

    private List<String> keys(AiGuardrailReservationCommand command, Window window) {
        String policy = hash(properties.policyVersion());
        String project = hash(command.projectId().toString());
        String user = hash(command.userId().toString());
        String feature = command.feature().configKey();
        String prefix = "wevo:ai:guardrail:" + policy + ":";
        return List.of(
                ledgerKey(command.idempotencyKey(), command.executionSequence()),
                prefix + "quota:user:" + user + ":minute:" + window.minuteId(),
                prefix + "quota:user:" + user + ":day:" + window.dayId(),
                prefix + "quota:project:" + project + ":minute:" + window.minuteId(),
                prefix + "quota:project:" + project + ":day:" + window.dayId(),
                prefix + "quota:feature:" + feature + ":minute:" + window.minuteId(),
                prefix + "quota:feature:" + feature + ":day:" + window.dayId(),
                prefix + "cost:project:" + project + ":day:" + window.dayId(),
                prefix + "cost:project:" + project + ":month:" + window.monthId()
        );
    }

    private String ledgerKey(AiJob job) {
        return ledgerKey(job.getIdempotencyKey(), job.getExecutionSequence());
    }

    private String ledgerKey(String idempotencyKey, int executionSequence) {
        return "wevo:ai:guardrail:" + hash(properties.policyVersion())
                + ":reservation:" + hash(idempotencyKey + ":" + executionSequence);
    }

    private Window window() {
        Instant now = Instant.now(clock);
        ZonedDateTime kst = now.atZone(KST);
        ZonedDateTime nextMinute = kst.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1);
        ZonedDateTime nextDay = kst.toLocalDate().plusDays(1).atStartOfDay(KST);
        ZonedDateTime nextMonth = kst.toLocalDate().withDayOfMonth(1)
                .plusMonths(1).atStartOfDay(KST);
        return new Window(
                MINUTE_FORMAT.format(kst), DAY_FORMAT.format(kst.toLocalDate()), MONTH_FORMAT.format(kst),
                seconds(now, nextMinute.toInstant()), seconds(now, nextDay.toInstant()),
                seconds(now, nextMonth.toInstant())
        );
    }

    private long seconds(Instant now, Instant boundary) {
        return Math.max(1L, Duration.between(now, boundary).toSeconds() + 1L);
    }

    private long toMicros(BigDecimal usd) {
        try {
            return usd.multiply(MICRO_USD).setScale(0, RoundingMode.CEILING).longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("AI 비용 설정이 지원 범위를 초과합니다.");
        }
    }

    private AiGuardrailExceededException exceeded(ErrorCode errorCode, long retryAfterSeconds) {
        return new AiGuardrailExceededException(errorCode, Math.max(1L, retryAfterSeconds));
    }

    private BusinessException unavailable() {
        return new BusinessException(ErrorCode.AI_GUARDRAIL_UNAVAILABLE);
    }

    private <T> T execute(DefaultRedisScript<T> script, List<String> keys, List<String> args) {
        try {
            return redisTemplate.execute(script, keys, args.toArray());
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static DefaultRedisScript<List> script(String source) {
        return new DefaultRedisScript(source, List.class);
    }

    private static DefaultRedisScript<Long> scriptLong(String source) {
        return new DefaultRedisScript<>(source, Long.class);
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    @Autowired(required = false)
    void setMetrics(AiOperationalMetrics metrics) {
        this.metrics = metrics;
    }

    private void recordLifecycleStateMismatch(AiJob job, String operation, long result) {
        if (result == 0 && metrics != null) {
            metrics.recordGuardrailLifecycleStateMismatch(job.getFeature(), operation);
        }
    }

    private String guardrailReason(BusinessException exception) {
        if (exception instanceof LedgerReservationConflictException) {
            return "reservation_state_conflict";
        }
        return switch (exception.getErrorCode()) {
            case AI_REQUEST_QUOTA_EXCEEDED -> "request_quota";
            case AI_PROJECT_QUOTA_EXCEEDED -> "project_quota";
            case AI_PROJECT_COST_BUDGET_EXCEEDED -> "budget";
            case AI_PRICING_NOT_CONFIGURED -> "pricing_missing";
            case AI_GUARDRAIL_UNAVAILABLE -> "redis_fail_closed";
            default -> "reservation_error";
        };
    }

    private static final class LedgerReservationConflictException extends BusinessException {

        private LedgerReservationConflictException() {
            super(ErrorCode.AI_GUARDRAIL_UNAVAILABLE);
        }
    }

    private record Window(
            String minuteId,
            String dayId,
            String monthId,
            long secondsUntilMinute,
            long secondsUntilDay,
            long secondsUntilMonth
    ) {
        long minuteTtlMs() {
            return Duration.ofSeconds(secondsUntilMinute).plusMinutes(1).toMillis();
        }

        long dayTtlMs() {
            return Duration.ofSeconds(secondsUntilDay).plusDays(1).toMillis();
        }

        long monthTtlMs() {
            return Duration.ofSeconds(secondsUntilMonth).plusDays(1).toMillis();
        }

        long ledgerTtlMs() {
            return Duration.ofSeconds(secondsUntilMonth).plusDays(2).toMillis();
        }
    }
}
