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

    /**
     * 입력 재대조와 결과 저장을 <b>한 트랜잭션에서</b> 처리한다.
     *
     * <p>현재 입력 해시는 호출자가 미리 계산해 넘기지 않고 {@link AiJobSnapshotProbe}로 <b>이 트랜잭션
     * 안에서</b> 계산한다 — 밖에서 계산하면 계산과 저장 사이에 의견·답변이 커밋돼도 AiJob 행 잠금으로는
     * 막히지 않아, 대조는 통과했지만 낡은 입력 기준의 결과가 저장될 수 있다. probe가 입력 쓰기 경로와
     * 같은 잠금(섹션 행)을 먼저 잡으므로, 이 트랜잭션이 시작된 뒤의 입력 변경은 반드시 대조에 반영된다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiJobCompletionResult succeed(
            UUID requestId,
            AiJobSnapshotProbe snapshotProbe,
            AiJobResultWriter resultWriter,
            LocalDateTime completedAt
    ) {
        AiJob job = findForUpdate(requestId);
        if (job.getStatus() != AiJobStatus.RUNNING) {
            throw new BusinessException(ErrorCode.AI_JOB_INVALID_STATE_TRANSITION);
        }
        if (!job.hasSameSnapshot(snapshotProbe.currentSnapshotHash())) {
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

    /**
     * 행 잠금 후 heartbeat가 <b>여전히</b> 기준 시각보다 오래됐을 때만 작업을 실패로 회수한다.
     *
     * <p>회수 스케줄러의 "오래된 작업 조회 → 실패 처리" 사이에 worker가 heartbeat를 갱신하는 경합에서,
     * 살아 있는 작업을 오회수(정상 AI 결과·비용 유실)하지 않도록 실패 전이 직전에 조건을 원자적으로
     * 재확인한다. worker의 {@link #heartbeat}도 같은 행을 잠그므로 둘은 직렬화된다.
     *
     * @return 실제로 회수(FAILED 전이)했으면 {@code true}, 조건 불충족으로 건너뛰었으면 {@code false}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean failIfHeartbeatStale(UUID requestId, LocalDateTime threshold, LocalDateTime failedAt) {
        AiJob job = findForUpdate(requestId);
        if (job.getStatus() != AiJobStatus.RUNNING) {
            return false;
        }
        LocalDateTime lastHeartbeatAt = job.getLastHeartbeatAt();
        if (lastHeartbeatAt != null && !lastHeartbeatAt.isBefore(threshold)) {
            return false; // 조회 이후 heartbeat가 갱신됨 — 살아 있는 작업이므로 회수하지 않는다.
        }
        job.fail(
                AiErrorType.WORKER_HEARTBEAT_TIMEOUT,
                "AI 작업 worker의 heartbeat 제한 시간이 초과되었습니다.",
                failedAt
        );
        return true;
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
