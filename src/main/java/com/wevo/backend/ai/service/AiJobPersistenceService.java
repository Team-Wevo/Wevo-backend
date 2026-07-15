package com.wevo.backend.ai.service;

import com.wevo.backend.ai.config.AiJobProperties;
import com.wevo.backend.ai.domain.AiErrorType;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.user.domain.User;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiJobPersistenceService {

    private final AiJobRepository repository;
    private final AiJobProperties properties;

    public AiJobPersistenceService(AiJobRepository repository, AiJobProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiJob insert(AiJob job) {
        return repository.saveAndFlush(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RetryResult retry(UUID requestId, User requestedBy, UUID newRequestId, LocalDateTime queuedAt) {
        AiJob requestedJob = findForUpdate(requestId);
        AiJob latest = repository.findLatestByIdempotencyKeyForUpdate(requestedJob.getIdempotencyKey())
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_JOB_NOT_FOUND));

        if (latest.getStatus() == AiJobStatus.QUEUED || latest.getStatus() == AiJobStatus.RUNNING) {
            return new RetryResult(latest, false);
        }
        if (latest.getStatus() == AiJobStatus.SUCCEEDED) {
            throw new BusinessException(ErrorCode.AI_JOB_INVALID_STATE_TRANSITION);
        }
        if (latest.getExecutionSequence() >= properties.maxExecutionsPerKey()) {
            throw new BusinessException(ErrorCode.AI_JOB_RETRY_LIMIT_EXCEEDED);
        }

        AiJob retry = AiJob.retry(newRequestId, latest, requestedBy, queuedAt);
        return new RetryResult(repository.saveAndFlush(retry), true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiJobStartResult start(UUID requestId, String currentSnapshotHash, LocalDateTime startedAt) {
        AiJob job = findForUpdate(requestId);
        if (job.getStatus() == AiJobStatus.RUNNING) {
            return new AiJobStartResult(job.getRequestId(), job.getStatus(), false);
        }
        if (job.getStatus() != AiJobStatus.QUEUED) {
            throw new BusinessException(ErrorCode.AI_JOB_INVALID_STATE_TRANSITION);
        }
        if (!job.hasSameSnapshot(currentSnapshotHash)) {
            job.markStale(startedAt);
            return new AiJobStartResult(job.getRequestId(), job.getStatus(), false);
        }
        job.start(startedAt);
        return new AiJobStartResult(job.getRequestId(), job.getStatus(), true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiJobCompletionResult succeed(
            UUID requestId,
            String currentSnapshotHash,
            AiJobResultWriter resultWriter,
            LocalDateTime completedAt
    ) {
        AiJob job = findForUpdate(requestId);
        if (job.getStatus() != AiJobStatus.RUNNING) {
            throw new BusinessException(ErrorCode.AI_JOB_INVALID_STATE_TRANSITION);
        }
        if (!job.hasSameSnapshot(currentSnapshotHash)) {
            job.markStale(completedAt);
            return new AiJobCompletionResult(job.getRequestId(), job.getStatus(), null, false);
        }
        Long resultId = resultWriter.persist();
        job.succeed(resultId, completedAt);
        return new AiJobCompletionResult(job.getRequestId(), job.getStatus(), resultId, true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void heartbeat(UUID requestId, LocalDateTime heartbeatAt) {
        findForUpdate(requestId).heartbeat(heartbeatAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(
            UUID requestId,
            AiErrorType errorType,
            String safeErrorMessage,
            LocalDateTime failedAt
    ) {
        findForUpdate(requestId).fail(errorType, safeErrorMessage, failedAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancel(UUID requestId, LocalDateTime cancelledAt) {
        findForUpdate(requestId).cancel(cancelledAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markStale(UUID requestId, LocalDateTime staleAt) {
        findForUpdate(requestId).markStale(staleAt);
    }

    private AiJob findForUpdate(UUID requestId) {
        return repository.findByRequestIdForUpdate(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_JOB_NOT_FOUND));
    }

    public record RetryResult(AiJob job, boolean created) {
    }
}
