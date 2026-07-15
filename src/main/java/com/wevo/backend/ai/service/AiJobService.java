package com.wevo.backend.ai.service;

import com.wevo.backend.ai.domain.AiErrorType;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.user.domain.User;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class AiJobService {

    private final AiJobRepository repository;
    private final AiJobPersistenceService persistenceService;
    private final AiJobIdempotencyKeyGenerator keyGenerator;
    private final AiErrorClassifier errorClassifier;
    private final AiErrorMessageSanitizer sanitizer;
    private final Clock clock;

    public AiJobService(
            AiJobRepository repository,
            AiJobPersistenceService persistenceService,
            AiJobIdempotencyKeyGenerator keyGenerator,
            AiErrorClassifier errorClassifier,
            AiErrorMessageSanitizer sanitizer,
            Clock clock
    ) {
        this.repository = repository;
        this.persistenceService = persistenceService;
        this.keyGenerator = keyGenerator;
        this.errorClassifier = errorClassifier;
        this.sanitizer = sanitizer;
        this.clock = clock;
    }

    /**
     * 동일한 출력 정체성의 최신 작업을 반환하고, 존재하지 않을 때만 최초 실행을 생성한다.
     * 실패·취소·stale 작업도 일반 재전송에서는 자동 재실행하지 않는다.
     */
    public AiJobCreateResult createOrGet(AiJobCreateCommand command) {
        Objects.requireNonNull(command, "command는 필수입니다.");
        String idempotencyKey = keyGenerator.generate(command.idempotencyInput());
        return repository.findTopByIdempotencyKeyOrderByExecutionSequenceDesc(idempotencyKey)
                .map(job -> AiJobCreateResult.from(job, false))
                .orElseGet(() -> createInitial(command, idempotencyKey));
    }

    /**
     * 실패·취소·stale 작업만 명시적으로 재실행한다. 성공 결과 강제 재생성은 AI-10의
     * 별도 권한·요청 계약 전까지 지원하지 않는다.
     */
    public AiJobCreateResult retry(UUID requestId, User requestedBy) {
        Objects.requireNonNull(requestId, "requestId는 필수입니다.");
        if (requestedBy == null || requestedBy.getId() == null) {
            throw new IllegalArgumentException("영속화된 requestedBy가 필요합니다.");
        }
        try {
            AiJobPersistenceService.RetryResult result = persistenceService.retry(
                    requestId, requestedBy, UUID.randomUUID(), now()
            );
            return AiJobCreateResult.from(result.job(), result.created());
        } catch (DataIntegrityViolationException exception) {
            AiJob requested = repository.findByRequestId(requestId)
                    .orElseThrow(() -> exception);
            AiJob latest = repository
                    .findTopByIdempotencyKeyOrderByExecutionSequenceDesc(requested.getIdempotencyKey())
                    .orElseThrow(() -> exception);
            return AiJobCreateResult.from(latest, false);
        }
    }

    public AiJobStartResult start(UUID requestId, String currentSnapshotHash) {
        requireSnapshotHash(currentSnapshotHash);
        return persistenceService.start(requestId, currentSnapshotHash, now());
    }

    public AiJobCompletionResult succeed(
            UUID requestId,
            String currentSnapshotHash,
            AiJobResultWriter resultWriter
    ) {
        requireSnapshotHash(currentSnapshotHash);
        Objects.requireNonNull(resultWriter, "resultWriter는 필수입니다.");
        return persistenceService.succeed(requestId, currentSnapshotHash, resultWriter, now());
    }

    public void heartbeat(UUID requestId) {
        persistenceService.heartbeat(requestId, now());
    }

    public void fail(UUID requestId, Throwable throwable) {
        Objects.requireNonNull(throwable, "throwable은 필수입니다.");
        AiErrorType errorType = errorClassifier.classify(throwable);
        String safeMessage = sanitizer.sanitize(errorClassifier.safeMessage(throwable));
        persistenceService.fail(requestId, errorType, safeMessage, now());
    }

    public void cancel(UUID requestId) {
        persistenceService.cancel(requestId, now());
    }

    public void markStale(UUID requestId) {
        persistenceService.markStale(requestId, now());
    }

    public void markApplicationTimedOut(UUID requestId) {
        persistenceService.fail(
                requestId,
                AiErrorType.APPLICATION_TIMEOUT,
                "AI 작업의 애플리케이션 제한 시간이 초과되었습니다.",
                now()
        );
    }

    public void markWorkerHeartbeatTimedOut(UUID requestId) {
        persistenceService.fail(
                requestId,
                AiErrorType.WORKER_HEARTBEAT_TIMEOUT,
                "AI 작업 worker의 heartbeat 제한 시간이 초과되었습니다.",
                now()
        );
    }

    public List<UUID> findNextQueuedRequestIds(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit은 1 이상이어야 합니다.");
        }
        return repository.findQueuedRequestIds(PageRequest.of(0, limit));
    }

    public List<UUID> findApplicationTimedOutRequestIds(LocalDateTime threshold) {
        Objects.requireNonNull(threshold, "threshold는 필수입니다.");
        return repository.findRequestIdsByStatusAndStartedAtBefore(AiJobStatus.RUNNING, threshold);
    }

    public List<UUID> findHeartbeatTimedOutRequestIds(LocalDateTime threshold) {
        Objects.requireNonNull(threshold, "threshold는 필수입니다.");
        return repository.findRequestIdsByStatusAndLastHeartbeatAtBefore(AiJobStatus.RUNNING, threshold);
    }

    private AiJobCreateResult createInitial(AiJobCreateCommand command, String idempotencyKey) {
        AiJob job = AiJob.queue(
                UUID.randomUUID(),
                command.project(),
                command.projectSection(),
                command.requestedBy(),
                command.feature(),
                command.inputSnapshotHash(),
                command.sourceVersion(),
                command.promptVersion(),
                command.schemaVersion(),
                command.modelId(),
                command.maxOutputTokens(),
                idempotencyKey,
                now()
        );
        try {
            return AiJobCreateResult.from(persistenceService.insert(job), true);
        } catch (DataIntegrityViolationException exception) {
            AiJob existing = repository.findTopByIdempotencyKeyOrderByExecutionSequenceDesc(idempotencyKey)
                    .orElseThrow(() -> exception);
            return AiJobCreateResult.from(existing, false);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private void requireSnapshotHash(String snapshotHash) {
        if (snapshotHash == null || !snapshotHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("currentSnapshotHash는 64자리 소문자 SHA-256 hex여야 합니다.");
        }
    }
}
