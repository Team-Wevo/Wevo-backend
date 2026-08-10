package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.AiUsageMetadata;
import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.domain.AiCostSnapshot;
import com.wevo.backend.ai.domain.AiErrorType;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiUsageLog;
import com.wevo.backend.ai.exception.AiAuditPersistenceException;
import com.wevo.backend.ai.operations.AiOperationalMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.UUID;

@Service
public class AiUsageService {

    private static final Logger log = LoggerFactory.getLogger(AiUsageService.class);

    private final AiUsagePersistenceService persistenceService;
    private final AiProperties aiProperties;
    private final AiCostCalculator costCalculator;
    private final AiErrorClassifier errorClassifier;
    private final AiErrorMessageSanitizer sanitizer;
    private final AiGuardrailService guardrailService;
    private final Clock clock;
    private AiOperationalMetrics metrics;

    public AiUsageService(
            AiUsagePersistenceService persistenceService,
            AiProperties aiProperties,
            AiCostCalculator costCalculator,
            AiErrorClassifier errorClassifier,
            AiErrorMessageSanitizer sanitizer,
            AiGuardrailService guardrailService,
            Clock clock
    ) {
        this.persistenceService = persistenceService;
        this.aiProperties = aiProperties;
        this.costCalculator = costCalculator;
        this.errorClassifier = errorClassifier;
        this.sanitizer = sanitizer;
        this.guardrailService = guardrailService;
        this.clock = clock;
    }

    public AiUsageHandle startRequest(AiUsageStartCommand command) {
        AiProperties.ModelOptions modelOptions = aiProperties.optionsFor(command.feature());
        String modelId = command.aiJob() == null ? modelOptions.model() : command.aiJob().getModelId();
        LocalDateTime startedAt = LocalDateTime.now(clock);
        AiUsageLog usageLog = AiUsageLog.start(
                UUID.randomUUID(),
                command.aiJob(),
                command.project(),
                command.projectSection(),
                command.requestedBy(),
                command.feature(),
                aiProperties.provider(),
                modelId,
                command.promptVersion(),
                command.inputSnapshotHash(),
                startedAt
        );
        try {
            AiUsageHandle persisted = persistenceService.start(usageLog);
            AiUsageHandle handle = new AiUsageHandle(
                    persisted.logId(), persisted.requestId(), command.aiJob(), command.feature(),
                    aiProperties.provider(), modelId, command.promptVersion(),
                    command.aiJob() == null ? "none" : command.aiJob().getSchemaVersion(), startedAt);
            guardrailService.markProviderStarted(command.aiJob());
            return handle;
        } catch (RuntimeException exception) {
            throw persistenceFailure(null, exception);
        }
    }

    public void completeSuccess(
            AiUsageHandle handle,
            AiUsageMetadata usage,
            int attemptCount,
            Long resultId
    ) {
        AiCostSnapshot cost = costCalculator.calculate(usage, handle.modelId());
        LocalDateTime completedAt = LocalDateTime.now(clock);
        try {
            persistenceService.completeSuccess(
                    handle.requestId(), usage, cost, attemptCount, resultId, completedAt
            );
            recordUsageWithRetry(handle.aiJob(), handle.requestId(), cost);
            recordSuccessMetric(handle, usage, cost, attemptCount, completedAt);
            logCompletion(handle, usage, attemptCount, "SUCCEEDED", null, completedAt);
        } catch (RuntimeException exception) {
            throw persistenceFailure(handle.requestId(), exception);
        }
    }

    public void completeFailure(
            AiUsageHandle handle,
            Throwable throwable,
            AiUsageMetadata usage,
            Integer attemptCount
    ) {
        AiCostSnapshot cost = costCalculator.calculate(usage, handle.modelId());
        AiErrorType errorType = errorClassifier.classify(throwable);
        String safeMessage = sanitizer.sanitize(errorClassifier.safeMessage(throwable));
        LocalDateTime completedAt = LocalDateTime.now(clock);
        completeFailure(
                handle.requestId(), handle.aiJob(), usage, cost, attemptCount,
                errorType, safeMessage, completedAt
        );
        recordFailureMetric(handle, usage, cost, attemptCount, errorType, completedAt);
        logCompletion(handle, usage, attemptCount, "FAILED", errorType, completedAt);
    }

    public void markOrphaned(UUID requestId, LocalDateTime completedAt) {
        String message = sanitizer.sanitize("제한 시간을 초과해 고아 요청으로 복구되었습니다.");
        completeFailure(
            requestId,
            null,
            null,
                AiCostSnapshot.unpriced(null),
                null,
                AiErrorType.ORPHANED_REQUEST,
                message,
                completedAt
        );
        if (metrics != null) {
            metrics.recordJobEvent(null, "orphan_recovered");
        }
    }

    private void completeFailure(
            UUID requestId,
            AiJob aiJob,
            AiUsageMetadata usage,
            AiCostSnapshot cost,
            Integer attemptCount,
            AiErrorType errorType,
            String errorMessage,
            LocalDateTime completedAt
    ) {
        try {
            persistenceService.completeFailure(
                    requestId, usage, cost, attemptCount, errorType, errorMessage, completedAt
            );
            recordUsageWithRetry(aiJob, requestId, cost);
        } catch (RuntimeException exception) {
            throw persistenceFailure(requestId, exception);
        }
    }

    private AiAuditPersistenceException persistenceFailure(UUID requestId, RuntimeException cause) {
        log.error(
                "Failed to persist AI usage audit. requestId={}, exceptionType={}",
                requestId,
                cause.getClass().getSimpleName()
        );
        if (cause instanceof AiAuditPersistenceException exception) {
            return exception;
        }
        return new AiAuditPersistenceException(cause);
    }

    private void recordUsageWithRetry(
            AiJob aiJob,
            UUID usageRequestId,
            AiCostSnapshot cost
    ) {
        RuntimeException lastFailure = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                guardrailService.recordUsage(aiJob, usageRequestId, cost);
                return;
            } catch (RuntimeException exception) {
                lastFailure = exception;
            }
        }
        throw lastFailure;
    }

    @Autowired(required = false)
    void setMetrics(AiOperationalMetrics metrics) {
        this.metrics = metrics;
    }

    private void recordSuccessMetric(
            AiUsageHandle handle,
            AiUsageMetadata usage,
            AiCostSnapshot cost,
            int attemptCount,
            LocalDateTime completedAt
    ) {
        if (metrics == null || handle.feature() == null) {
            return;
        }
        metrics.recordSuccess(
                handle.feature(), provider(handle, usage), model(handle, usage),
                handle.promptVersion(), handle.schemaVersion(), duration(handle, completedAt),
                attemptCount, usage, cost, handle.aiJob() != null);
    }

    private void recordFailureMetric(
            AiUsageHandle handle,
            AiUsageMetadata usage,
            AiCostSnapshot cost,
            Integer attemptCount,
            AiErrorType errorType,
            LocalDateTime completedAt
    ) {
        if (metrics == null || handle.feature() == null) {
            return;
        }
        metrics.recordFailure(
                handle.feature(), provider(handle, usage), model(handle, usage),
                handle.promptVersion(), handle.schemaVersion(), duration(handle, completedAt),
                attemptCount, errorType, usage, cost, handle.aiJob() != null);
    }

    private void logCompletion(
            AiUsageHandle handle,
            AiUsageMetadata usage,
            Integer attempts,
            String status,
            AiErrorType errorType,
            LocalDateTime completedAt
    ) {
        log.atInfo()
                .addKeyValue("requestId", handle.requestId())
                .addKeyValue("aiJobRequestId", handle.aiJob() == null ? null : handle.aiJob().getRequestId())
                .addKeyValue("providerRequestId", usage == null ? null : usage.providerRequestId())
                .addKeyValue("feature", handle.feature())
                .addKeyValue("status", status)
                .addKeyValue("errorType", errorType)
                .addKeyValue("model", model(handle, usage))
                .addKeyValue("promptVersion", handle.promptVersion())
                .addKeyValue("schemaVersion", handle.schemaVersion())
                .addKeyValue("latencyMs", duration(handle, completedAt).toMillis())
                .addKeyValue("attempt", attempts)
                .log("AI invocation completed");
    }

    private String provider(AiUsageHandle handle, AiUsageMetadata usage) {
        return usage != null && usage.providerId() != null ? usage.providerId() : handle.provider();
    }

    private String model(AiUsageHandle handle, AiUsageMetadata usage) {
        return usage != null && usage.modelId() != null ? usage.modelId() : handle.modelId();
    }

    private Duration duration(AiUsageHandle handle, LocalDateTime completedAt) {
        return handle.startedAt() == null ? Duration.ZERO : Duration.between(handle.startedAt(), completedAt);
    }
}
