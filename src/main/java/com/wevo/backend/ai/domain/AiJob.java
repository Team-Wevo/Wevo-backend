package com.wevo.backend.ai.domain;

import com.wevo.backend.global.common.BaseTimeEntity;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 하나의 논리적인 AI 기능 실행을 추적하는 영속 작업.
 *
 * <p>원본 prompt, completion, 사용자 의견 전문은 저장하지 않는다. Provider 호출별
 * token/비용 감사는 {@link AiUsageLog}가 별도로 담당한다.</p>
 */
@Entity
@Table(
        name = "ai_jobs",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ai_jobs_request_id", columnNames = "request_id"),
                @UniqueConstraint(
                        name = "uk_ai_jobs_idempotency_execution",
                        columnNames = {"idempotency_key", "execution_sequence"}
                )
        },
        indexes = {
                @Index(name = "idx_ai_jobs_status_created", columnList = "status, created_at"),
                @Index(name = "idx_ai_jobs_status_started", columnList = "status, started_at"),
                @Index(name = "idx_ai_jobs_status_heartbeat", columnList = "status, last_heartbeat_at"),
                @Index(name = "idx_ai_jobs_project_created", columnList = "project_id, created_at"),
                @Index(name = "idx_ai_jobs_section_created", columnList = "project_section_id, created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiJob extends BaseTimeEntity {

    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_section_id")
    private ProjectSection projectSection;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_user_id", nullable = false)
    private User requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature", nullable = false, length = 50)
    private AiFeature feature;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AiJobStatus status;

    @Column(name = "input_snapshot_hash", nullable = false, length = 64)
    private String inputSnapshotHash;

    @Column(name = "source_version", nullable = false, length = 100)
    private String sourceVersion;

    @Column(name = "prompt_version", nullable = false, length = 100)
    private String promptVersion;

    @Column(name = "schema_version", nullable = false, length = 100)
    private String schemaVersion;

    @Column(name = "model_id", nullable = false, length = 100)
    private String modelId;

    @Column(name = "max_output_tokens", nullable = false)
    private Integer maxOutputTokens;

    @Column(name = "rollout_id", nullable = false, length = 100)
    private String rolloutId;

    @Column(name = "reasoning_effort", nullable = false, length = 30)
    private String reasoningEffort;

    @Column(name = "pricing_version", nullable = false, length = 100)
    private String pricingVersion;

    @Column(name = "policy_version", nullable = false, length = 100)
    private String policyVersion;

    @Column(name = "idempotency_key", nullable = false, length = 64, updatable = false)
    private String idempotencyKey;

    @Column(name = "execution_sequence", nullable = false, updatable = false)
    private Integer executionSequence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "retry_of_job_id")
    private AiJob retryOf;

    @Column(name = "result_id")
    private Long resultId;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_error_type", length = 50)
    private AiErrorType finalErrorType;

    @Column(name = "safe_error_message", length = 500)
    private String safeErrorMessage;

    @Column(name = "queued_at", nullable = false)
    private LocalDateTime queuedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "last_heartbeat_at")
    private LocalDateTime lastHeartbeatAt;

    private AiJob(
            UUID requestId,
            Project project,
            ProjectSection projectSection,
            User requestedBy,
            AiFeature feature,
            String inputSnapshotHash,
            String sourceVersion,
            String promptVersion,
            String schemaVersion,
            String modelId,
            int maxOutputTokens,
            String rolloutId,
            String reasoningEffort,
            String pricingVersion,
            String policyVersion,
            String idempotencyKey,
            int executionSequence,
            AiJob retryOf,
            LocalDateTime queuedAt
    ) {
        this.requestId = Objects.requireNonNull(requestId, "requestId는 필수입니다.");
        this.project = Objects.requireNonNull(project, "project는 필수입니다.");
        this.projectSection = projectSection;
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy는 필수입니다.");
        this.feature = Objects.requireNonNull(feature, "feature는 필수입니다.");
        this.inputSnapshotHash = requireSha256(inputSnapshotHash, "inputSnapshotHash");
        this.sourceVersion = requireText(sourceVersion, "sourceVersion", 100);
        this.promptVersion = requireText(promptVersion, "promptVersion", 100);
        this.schemaVersion = requireText(schemaVersion, "schemaVersion", 100);
        this.modelId = requireText(modelId, "modelId", 100);
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens는 1 이상이어야 합니다.");
        }
        this.maxOutputTokens = maxOutputTokens;
        this.rolloutId = requireText(rolloutId, "rolloutId", 100);
        this.reasoningEffort = requireText(reasoningEffort, "reasoningEffort", 30);
        this.pricingVersion = requireText(pricingVersion, "pricingVersion", 100);
        this.policyVersion = requireText(policyVersion, "policyVersion", 100);
        this.idempotencyKey = requireSha256(idempotencyKey, "idempotencyKey");
        if (executionSequence <= 0) {
            throw new IllegalArgumentException("executionSequence는 1 이상이어야 합니다.");
        }
        this.executionSequence = executionSequence;
        this.retryOf = retryOf;
        this.queuedAt = Objects.requireNonNull(queuedAt, "queuedAt는 필수입니다.");
        this.status = AiJobStatus.QUEUED;
        this.attemptCount = 0;
    }

    public static AiJob queue(
            UUID requestId,
            Project project,
            ProjectSection projectSection,
            User requestedBy,
            AiFeature feature,
            String inputSnapshotHash,
            String sourceVersion,
            String promptVersion,
            String schemaVersion,
            String modelId,
            int maxOutputTokens,
            String rolloutId,
            String reasoningEffort,
            String pricingVersion,
            String policyVersion,
            String idempotencyKey,
            LocalDateTime queuedAt
    ) {
        return new AiJob(
                requestId, project, projectSection, requestedBy, feature, inputSnapshotHash,
                sourceVersion, promptVersion, schemaVersion, modelId, maxOutputTokens,
                rolloutId, reasoningEffort, pricingVersion, policyVersion,
                idempotencyKey, 1, null, queuedAt
        );
    }

    public static AiJob queue(
            UUID requestId,
            Project project,
            ProjectSection projectSection,
            User requestedBy,
            AiFeature feature,
            String inputSnapshotHash,
            String sourceVersion,
            String promptVersion,
            String schemaVersion,
            String modelId,
            int maxOutputTokens,
            String idempotencyKey,
            LocalDateTime queuedAt
    ) {
        return queue(requestId, project, projectSection, requestedBy, feature, inputSnapshotHash,
                sourceVersion, promptVersion, schemaVersion, modelId, maxOutputTokens,
                "baseline", "none", "unpriced", "guardrails-disabled", idempotencyKey, queuedAt);
    }

    public static AiJob retry(UUID requestId, AiJob previous, User requestedBy, LocalDateTime queuedAt) {
        Objects.requireNonNull(previous, "previous는 필수입니다.");
        if (!previous.isRetryable()) {
            throw new BusinessException(ErrorCode.AI_JOB_INVALID_STATE_TRANSITION);
        }
        return new AiJob(
                requestId,
                previous.project,
                previous.projectSection,
                requestedBy,
                previous.feature,
                previous.inputSnapshotHash,
                previous.sourceVersion,
                previous.promptVersion,
                previous.schemaVersion,
                previous.modelId,
                previous.maxOutputTokens,
                previous.rolloutId,
                previous.reasoningEffort,
                previous.pricingVersion,
                previous.policyVersion,
                previous.idempotencyKey,
                previous.executionSequence + 1,
                previous,
                queuedAt
        );
    }

    public void start(LocalDateTime startedAt) {
        requireStatus(AiJobStatus.QUEUED);
        LocalDateTime startTime = Objects.requireNonNull(startedAt, "startedAt는 필수입니다.");
        this.status = AiJobStatus.RUNNING;
        this.startedAt = startTime;
        this.lastHeartbeatAt = startTime;
        this.attemptCount += 1;
    }

    public void heartbeat(LocalDateTime heartbeatAt) {
        requireStatus(AiJobStatus.RUNNING);
        this.lastHeartbeatAt = Objects.requireNonNull(heartbeatAt, "heartbeatAt는 필수입니다.");
    }

    public void succeed(Long resultId, LocalDateTime completedAt) {
        requireStatus(AiJobStatus.RUNNING);
        if (resultId == null || resultId <= 0) {
            throw new IllegalArgumentException("resultId는 1 이상이어야 합니다.");
        }
        this.resultId = resultId;
        finish(AiJobStatus.SUCCEEDED, completedAt);
    }

    public void fail(AiErrorType errorType, String safeErrorMessage, LocalDateTime failedAt) {
        requireStatus(AiJobStatus.RUNNING);
        this.finalErrorType = Objects.requireNonNull(errorType, "errorType은 필수입니다.");
        this.safeErrorMessage = requireText(safeErrorMessage, "safeErrorMessage", 500);
        this.failedAt = Objects.requireNonNull(failedAt, "failedAt는 필수입니다.");
        finish(AiJobStatus.FAILED, failedAt);
    }

    public void cancel(LocalDateTime cancelledAt) {
        requireQueuedOrRunning();
        this.cancelledAt = Objects.requireNonNull(cancelledAt, "cancelledAt는 필수입니다.");
        finish(AiJobStatus.CANCELLED, cancelledAt);
    }

    public void markStale(LocalDateTime staleAt) {
        requireQueuedOrRunning();
        this.finalErrorType = AiErrorType.STALE_INPUT;
        this.safeErrorMessage = "AI 작업 입력이 최신 상태와 일치하지 않습니다.";
        finish(AiJobStatus.STALE, staleAt);
    }

    public boolean hasSameSnapshot(String currentSnapshotHash) {
        return inputSnapshotHash.equals(currentSnapshotHash);
    }

    public boolean isRetryable() {
        return status == AiJobStatus.FAILED
                || status == AiJobStatus.CANCELLED
                || status == AiJobStatus.STALE;
    }

    private void finish(AiJobStatus targetStatus, LocalDateTime completedAt) {
        this.status = targetStatus;
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt는 필수입니다.");
    }

    private void requireStatus(AiJobStatus expected) {
        if (status != expected) {
            throw new BusinessException(ErrorCode.AI_JOB_INVALID_STATE_TRANSITION);
        }
    }

    private void requireQueuedOrRunning() {
        if (status != AiJobStatus.QUEUED && status != AiJobStatus.RUNNING) {
            throw new BusinessException(ErrorCode.AI_JOB_INVALID_STATE_TRANSITION);
        }
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "는 필수입니다.");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + "는 " + maxLength + "자 이하여야 합니다.");
        }
        return value;
    }

    private static String requireSha256(String value, String field) {
        if (value == null || !SHA_256_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(field + "는 64자리 소문자 SHA-256 hex여야 합니다.");
        }
        return value;
    }
}
