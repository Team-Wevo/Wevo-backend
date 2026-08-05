package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.AiUsageMetadata;
import com.wevo.backend.ai.domain.AiCostSnapshot;
import com.wevo.backend.ai.domain.AiErrorType;
import com.wevo.backend.ai.domain.AiUsageLog;
import com.wevo.backend.ai.repository.AiUsageLogRepository;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AiUsagePersistenceService {

    private final AiUsageLogRepository repository;

    public AiUsagePersistenceService(AiUsageLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiUsageHandle start(AiUsageLog log) {
        AiUsageLog saved = repository.save(log);
        return new AiUsageHandle(saved.getId(), saved.getRequestId(), saved.getAiJob());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeSuccess(
            UUID requestId,
            AiUsageMetadata usage,
            AiCostSnapshot cost,
            int attemptCount,
            Long resultId,
            LocalDateTime completedAt
    ) {
        AiUsageLog log = findForUpdate(requestId);
        log.completeSuccess(usage, cost, attemptCount, resultId, completedAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeFailure(
            UUID requestId,
            AiUsageMetadata usage,
            AiCostSnapshot cost,
            Integer attemptCount,
            AiErrorType errorType,
            String errorMessage,
            LocalDateTime completedAt
    ) {
        AiUsageLog log = findForUpdate(requestId);
        log.completeFailure(usage, cost, attemptCount, errorType, errorMessage, completedAt);
    }

    private AiUsageLog findForUpdate(UUID requestId) {
        return repository.findByRequestIdForUpdate(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_USAGE_LOG_NOT_FOUND));
    }
}
