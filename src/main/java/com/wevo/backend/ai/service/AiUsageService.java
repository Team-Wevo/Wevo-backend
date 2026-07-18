package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.AiUsageMetadata;
import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.domain.AiCostSnapshot;
import com.wevo.backend.ai.domain.AiErrorType;
import com.wevo.backend.ai.domain.AiUsageLog;
import com.wevo.backend.ai.exception.AiAuditPersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AiUsageService {

    private static final Logger log = LoggerFactory.getLogger(AiUsageService.class);

    private final AiUsagePersistenceService persistenceService;
    private final AiProperties aiProperties;
    private final AiCostCalculator costCalculator;
    private final AiErrorClassifier errorClassifier;
    private final AiErrorMessageSanitizer sanitizer;
    private final Clock clock;

    public AiUsageService(
            AiUsagePersistenceService persistenceService,
            AiProperties aiProperties,
            AiCostCalculator costCalculator,
            AiErrorClassifier errorClassifier,
            AiErrorMessageSanitizer sanitizer,
            Clock clock
    ) {
        this.persistenceService = persistenceService;
        this.aiProperties = aiProperties;
        this.costCalculator = costCalculator;
        this.errorClassifier = errorClassifier;
        this.sanitizer = sanitizer;
        this.clock = clock;
    }

    public AiUsageHandle startRequest(AiUsageStartCommand command) {
        AiProperties.ModelOptions modelOptions = aiProperties.optionsFor(command.feature());
        String modelId = modelOptions.model();
        if (command.aiJob() != null
                && (!command.aiJob().getModelId().equals(modelId)
                || !command.aiJob().getMaxOutputTokens().equals(modelOptions.maxOutputTokens()))) {
            throw new IllegalArgumentException("AiJob과 감사 로그의 model 실행 정책이 일치해야 합니다.");
        }
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
                LocalDateTime.now(clock)
        );
        try {
            return persistenceService.start(usageLog);
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
        AiCostSnapshot cost = costCalculator.calculate(usage);
        try {
            persistenceService.completeSuccess(
                    handle.requestId(), usage, cost, attemptCount, resultId, LocalDateTime.now(clock)
            );
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
        AiCostSnapshot cost = costCalculator.calculate(usage);
        AiErrorType errorType = errorClassifier.classify(throwable);
        String safeMessage = sanitizer.sanitize(errorClassifier.safeMessage(throwable));
        completeFailure(
                handle.requestId(), usage, cost, attemptCount, errorType, safeMessage, LocalDateTime.now(clock)
        );
    }

    public void markOrphaned(UUID requestId, LocalDateTime completedAt) {
        String message = sanitizer.sanitize("제한 시간을 초과해 고아 요청으로 복구되었습니다.");
        completeFailure(
                requestId,
                null,
                AiCostSnapshot.unpriced(null),
                null,
                AiErrorType.ORPHANED_REQUEST,
                message,
                completedAt
        );
    }

    private void completeFailure(
            UUID requestId,
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
}
