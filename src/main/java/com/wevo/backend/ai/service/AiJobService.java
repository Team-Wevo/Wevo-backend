package com.wevo.backend.ai.service;

import com.wevo.backend.ai.domain.AiErrorType;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.user.domain.User;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
    private final AiExecutionAvailabilityGuard availabilityGuard;
    private final Clock clock;

    public AiJobService(
            AiJobRepository repository,
            AiJobPersistenceService persistenceService,
            AiJobIdempotencyKeyGenerator keyGenerator,
            AiErrorClassifier errorClassifier,
            AiErrorMessageSanitizer sanitizer,
            AiExecutionAvailabilityGuard availabilityGuard,
            Clock clock
    ) {
        this.repository = repository;
        this.persistenceService = persistenceService;
        this.keyGenerator = keyGenerator;
        this.errorClassifier = errorClassifier;
        this.sanitizer = sanitizer;
        this.availabilityGuard = availabilityGuard;
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
                .orElseGet(() -> {
                    availabilityGuard.requireAvailable();
                    return createInitial(command, idempotencyKey);
                });
    }

    /**
     * 동일한 출력 정체성의 최신 작업을 <b>생성 없이</b> 조회한다. 실행 API가 상태 검증(③)에 앞서
     * 멱등 재사용(②)을 판정할 때 사용한다 — 재사용 가능한 작업이 있으면 상태 검증 없이 그 작업을
     * 반환하고, 없거나 종료(실패·취소·stale) 상태일 때만 새 작업을 생성/재시도한다.
     */
    public Optional<AiJobCreateResult> findLatest(AiJobIdempotencyInput idempotencyInput) {
        Objects.requireNonNull(idempotencyInput, "idempotencyInput은 필수입니다.");
        String idempotencyKey = keyGenerator.generate(idempotencyInput);
        return repository.findTopByIdempotencyKeyOrderByExecutionSequenceDesc(idempotencyKey)
                .map(job -> AiJobCreateResult.from(job, false));
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
        availabilityGuard.requireAvailable();
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

    /**
     * 완료 처리 트랜잭션 안에서 입력을 재대조하고, 통과했을 때만 결과를 저장한다.
     *
     * <p>현재 입력 해시는 {@code snapshotProbe}로 <b>저장 트랜잭션 안에서</b> 계산된다
     * ({@link AiJobSnapshotProbe} 참고 — 계산과 저장 사이의 입력 변경을 막기 위한 계약이다).
     */
    public AiJobCompletionResult succeed(
            UUID requestId,
            AiJobSnapshotProbe snapshotProbe,
            AiJobResultWriter resultWriter
    ) {
        Objects.requireNonNull(snapshotProbe, "snapshotProbe는 필수입니다.");
        Objects.requireNonNull(resultWriter, "resultWriter는 필수입니다.");
        return persistenceService.succeed(
                requestId,
                () -> requireSnapshotHash(snapshotProbe.currentSnapshotHash()),
                resultWriter,
                now());
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

    /**
     * heartbeat가 끊긴 RUNNING 작업을 <b>실패 직전에 조건을 원자적으로 재확인</b>하고 회수한다.
     * 조회 이후 heartbeat가 갱신된 살아 있는 작업은 회수하지 않는다(오회수 방지 — 결과·비용 유실 방지).
     *
     * @return 실제로 회수했으면 {@code true}
     */
    public boolean recoverIfHeartbeatStale(UUID requestId, LocalDateTime threshold) {
        Objects.requireNonNull(threshold, "threshold는 필수입니다.");
        return persistenceService.failIfHeartbeatStale(requestId, threshold, now());
    }

    public List<UUID> findNextQueuedRequestIds(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit은 1 이상이어야 합니다.");
        }
        return repository.findQueuedRequestIds(PageRequest.of(0, limit));
    }

    /**
     * 실행 가능한(핸들러가 등록된) 기능의 QUEUED 작업만 오래된 순으로 조회한다. 미지원 기능 작업이
     * 큐 앞을 점유해 지원 작업이 굶는 것을 막는다.
     */
    public List<UUID> findNextQueuedRequestIds(Collection<AiFeature> features, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit은 1 이상이어야 합니다.");
        }
        if (features == null || features.isEmpty()) {
            return List.of();
        }
        return repository.findQueuedRequestIdsByFeatureIn(features, PageRequest.of(0, limit));
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

    private String requireSnapshotHash(String snapshotHash) {
        if (snapshotHash == null || !snapshotHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("currentSnapshotHash는 64자리 소문자 SHA-256 hex여야 합니다.");
        }
        return snapshotHash;
    }
}
