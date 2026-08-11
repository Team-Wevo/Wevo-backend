package com.wevo.backend.ai.domain;

import com.wevo.backend.ai.client.AiUsageMetadata;
import com.wevo.backend.global.common.BaseTimeEntity;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.user.domain.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 기능 호출 사용량/비용 로그. 토큰 수와 추정 비용, 요청 상태를 기록한다.
 */
@Entity
@Table(
        name = "ai_usage_logs",
        uniqueConstraints = @UniqueConstraint(name = "uk_ai_usage_logs_request_id", columnNames = "request_id"),
        indexes = {
                @Index(name = "idx_ai_usage_logs_project_created", columnList = "project_id, created_at"),
                @Index(name = "idx_ai_usage_logs_section_created", columnList = "project_section_id, created_at"),
                @Index(name = "idx_ai_usage_logs_status_started", columnList = "request_status, started_at"),
                @Index(name = "idx_ai_usage_logs_feature_created", columnList = "feature, created_at"),
                @Index(name = "idx_ai_usage_logs_job_created", columnList = "ai_job_id, created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiUsageLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Column(name = "provider_request_id", length = 200)
    private String providerRequestId;

    @Column(name = "provider", nullable = false, length = 30)
    private String provider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_job_id")
    private AiJob aiJob;

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

    @Column(name = "model_id", nullable = false, length = 100)
    private String modelId;

    @Column(name = "prompt_version", nullable = false, length = 100)
    private String promptVersion;

    @Column(name = "rollout_id", length = 100)
    private String rolloutId;

    @Column(name = "reasoning_effort", length = 30)
    private String reasoningEffort;

    @Column(name = "policy_version", length = 100)
    private String policyVersion;

    @Column(name = "input_snapshot_hash", nullable = false, length = 64)
    private String inputSnapshotHash;

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(name = "cache_read_input_tokens")
    private Long cacheReadInputTokens;

    @Column(name = "cache_write_input_tokens")
    private Long cacheWriteInputTokens;

    @Column(name = "reasoning_tokens")
    private Long reasoningTokens;

    @Column(name = "pricing_version", length = 100)
    private String pricingVersion;

    @Column(name = "input_price_per_million_tokens", precision = 24, scale = 12)
    private BigDecimal inputPricePerMillionTokens;

    @Column(name = "output_price_per_million_tokens", precision = 24, scale = 12)
    private BigDecimal outputPricePerMillionTokens;

    @Column(name = "cache_read_price_per_million_tokens", precision = 24, scale = 12)
    private BigDecimal cacheReadPricePerMillionTokens;

    @Column(name = "cache_write_price_per_million_tokens", precision = 24, scale = 12)
    private BigDecimal cacheWritePricePerMillionTokens;

    @Column(name = "estimated_cost", precision = 24, scale = 12)
    private BigDecimal estimatedCost;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_status", nullable = false, length = 20)
    private AiRequestStatus requestStatus;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "attempt_count")
    private Integer attemptCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_type", length = 50)
    private AiErrorType errorType;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "result_id")
    private Long resultId;

    private AiUsageLog(
            UUID requestId,
            AiJob aiJob,
            Project project,
            ProjectSection projectSection,
            User requestedBy,
            AiFeature feature,
            String provider,
            String modelId,
            String promptVersion,
            String inputSnapshotHash,
            LocalDateTime startedAt
    ) {
        this.requestId = Objects.requireNonNull(requestId, "requestId는 필수입니다.");
        this.aiJob = aiJob;
        this.project = Objects.requireNonNull(project, "project는 필수입니다.");
        this.projectSection = projectSection;
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy는 필수입니다.");
        this.feature = Objects.requireNonNull(feature, "feature는 필수입니다.");
        this.provider = requireText(provider, "provider");
        if (this.provider.length() > 30) {
            throw new IllegalArgumentException("provider는 30자 이하여야 합니다.");
        }
        this.modelId = requireText(modelId, "modelId");
        this.promptVersion = requireText(promptVersion, "promptVersion");
        if (aiJob != null) {
            this.rolloutId = aiJob.getRolloutId();
            this.reasoningEffort = aiJob.getReasoningEffort();
            this.policyVersion = aiJob.getPolicyVersion();
        }
        this.inputSnapshotHash = requireText(inputSnapshotHash, "inputSnapshotHash");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt는 필수입니다.");
        this.requestStatus = AiRequestStatus.REQUESTED;
    }

    public static AiUsageLog start(
            UUID requestId,
            Project project,
            ProjectSection projectSection,
            User requestedBy,
            AiFeature feature,
            String provider,
            String modelId,
            String promptVersion,
            String inputSnapshotHash,
            LocalDateTime startedAt
    ) {
        return start(
                requestId, null, project, projectSection, requestedBy, feature,
                provider, modelId, promptVersion, inputSnapshotHash, startedAt
        );
    }

    public static AiUsageLog start(
            UUID requestId,
            AiJob aiJob,
            Project project,
            ProjectSection projectSection,
            User requestedBy,
            AiFeature feature,
            String provider,
            String modelId,
            String promptVersion,
            String inputSnapshotHash,
            LocalDateTime startedAt
    ) {
        return new AiUsageLog(
                requestId, aiJob, project, projectSection, requestedBy, feature,
                provider, modelId, promptVersion, inputSnapshotHash, startedAt
        );
    }

    public void completeSuccess(
            AiUsageMetadata usage,
            AiCostSnapshot cost,
            int attemptCount,
            Long resultId,
            LocalDateTime completedAt
    ) {
        requireRequested();
        if (attemptCount <= 0) {
            throw new IllegalArgumentException("attemptCount는 1 이상이어야 합니다.");
        }
        applyUsageAndCost(usage, cost);
        this.attemptCount = attemptCount;
        this.resultId = resultId;
        finish(AiRequestStatus.SUCCEEDED, completedAt);
    }

    public void completeFailure(
            AiUsageMetadata usage,
            AiCostSnapshot cost,
            Integer attemptCount,
            AiErrorType errorType,
            String errorMessage,
            LocalDateTime completedAt
    ) {
        requireRequested();
        if (attemptCount != null && attemptCount <= 0) {
            throw new IllegalArgumentException("attemptCount는 null 또는 1 이상이어야 합니다.");
        }
        applyUsageAndCost(usage, cost);
        this.attemptCount = attemptCount;
        this.errorType = Objects.requireNonNull(errorType, "errorType는 필수입니다.");
        this.errorMessage = errorMessage;
        finish(AiRequestStatus.FAILED, completedAt);
    }

    public Long getTotalInputTokens() {
        if (inputTokens == null) {
            return null;
        }
        return inputTokens
                + (cacheReadInputTokens == null ? 0L : cacheReadInputTokens)
                + (cacheWriteInputTokens == null ? 0L : cacheWriteInputTokens);
    }

    /** Redis ledger 재생에 사용하는 DB 비용 snapshot. 원문 prompt/completion은 포함하지 않는다. */
    public AiCostSnapshot costSnapshot() {
        return new AiCostSnapshot(
                pricingVersion,
                inputPricePerMillionTokens,
                outputPricePerMillionTokens,
                cacheReadPricePerMillionTokens,
                cacheWritePricePerMillionTokens,
                estimatedCost
        );
    }

    private void applyUsageAndCost(AiUsageMetadata usage, AiCostSnapshot cost) {
        if (usage != null) {
            if (usage.providerId() != null) {
                this.provider = usage.providerId();
            }
            this.providerRequestId = usage.providerRequestId();
            if (usage.modelId() != null) {
                this.modelId = usage.modelId();
            }
            this.inputTokens = usage.inputTokens();
            this.outputTokens = usage.outputTokens();
            this.cacheReadInputTokens = usage.cacheReadInputTokens();
            this.cacheWriteInputTokens = usage.cacheWriteInputTokens();
            this.reasoningTokens = usage.reasoningTokens();
        }
        if (cost != null) {
            this.pricingVersion = cost.pricingVersion();
            this.inputPricePerMillionTokens = cost.inputPricePerMillionTokens();
            this.outputPricePerMillionTokens = cost.outputPricePerMillionTokens();
            this.cacheReadPricePerMillionTokens = cost.cacheReadPricePerMillionTokens();
            this.cacheWritePricePerMillionTokens = cost.cacheWritePricePerMillionTokens();
            this.estimatedCost = cost.estimatedCost();
        }
    }

    private void finish(AiRequestStatus status, LocalDateTime completedAt) {
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt는 필수입니다.");
        this.latencyMs = Math.max(0L, Duration.between(startedAt, completedAt).toMillis());
        this.requestStatus = status;
    }

    private void requireRequested() {
        if (requestStatus != AiRequestStatus.REQUESTED) {
            throw new IllegalStateException("완료된 AI 요청의 상태를 다시 변경할 수 없습니다.");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "는 필수입니다.");
        }
        return value;
    }
}
