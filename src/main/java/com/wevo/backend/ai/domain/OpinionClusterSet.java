package com.wevo.backend.ai.domain;

import com.wevo.backend.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 성공한 의견 분류 실행의 불변 versioned 결과 set. */
@Entity
@Table(name = "opinion_cluster_sets", uniqueConstraints = @UniqueConstraint(
        name = "uk_opinion_cluster_sets_source_job", columnNames = "source_ai_job_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OpinionClusterSet extends BaseTimeEntity {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_ai_job_id", nullable = false, updatable = false)
    private AiJob sourceJob;

    @Column(name = "project_section_id", nullable = false, updatable = false)
    private Long projectSectionId;

    @Column(name = "opinion_gate_generation", nullable = false, updatable = false)
    private long opinionGateGeneration;

    @Column(name = "input_snapshot_hash", length = 64, nullable = false, updatable = false)
    private String inputSnapshotHash;

    @Column(name = "source_version", length = 100, nullable = false, updatable = false)
    private String sourceVersion;

    @Column(name = "prompt_version", length = 100, nullable = false, updatable = false)
    private String promptVersion;

    @Column(name = "schema_version", length = 100, nullable = false, updatable = false)
    private String schemaVersion;

    @Column(name = "model_id", length = 100, nullable = false, updatable = false)
    private String modelId;

    @Column(name = "total_opinion_count", nullable = false, updatable = false)
    private int totalOpinionCount;

    @Builder
    private OpinionClusterSet(
            AiJob sourceJob,
            Long projectSectionId,
            long opinionGateGeneration,
            String inputSnapshotHash,
            String sourceVersion,
            String promptVersion,
            String schemaVersion,
            String modelId,
            int totalOpinionCount
    ) {
        this.sourceJob = Objects.requireNonNull(sourceJob, "sourceJob은 필수입니다.");
        this.projectSectionId = Objects.requireNonNull(projectSectionId, "projectSectionId는 필수입니다.");
        if (opinionGateGeneration < 0 || totalOpinionCount <= 0) {
            throw new IllegalArgumentException("마감 세대와 전체 의견 수가 유효하지 않습니다.");
        }
        this.opinionGateGeneration = opinionGateGeneration;
        this.totalOpinionCount = totalOpinionCount;
        this.inputSnapshotHash = requireHash(inputSnapshotHash);
        this.sourceVersion = requireText(sourceVersion, "sourceVersion");
        this.promptVersion = requireText(promptVersion, "promptVersion");
        this.schemaVersion = requireText(schemaVersion, "schemaVersion");
        this.modelId = requireText(modelId, "modelId");
    }

    private String requireHash(String value) {
        if (value == null || !SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException("inputSnapshotHash가 유효하지 않습니다.");
        }
        return value;
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 100) {
            throw new IllegalArgumentException(field + "가 유효하지 않습니다.");
        }
        return value;
    }
}
