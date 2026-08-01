package com.wevo.backend.ai.domain;

import com.wevo.backend.global.common.BaseTimeEntity;
import com.wevo.backend.project.domain.Project;
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

/** 성공한 프로젝트 전체 흐름 점검의 불변 결과 root. */
@Entity
@Table(name = "project_flow_checks", uniqueConstraints =
        @UniqueConstraint(name = "uk_project_flow_checks_source_job", columnNames = "source_ai_job_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectFlowCheck extends BaseTimeEntity {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_ai_job_id", nullable = false, updatable = false) private AiJob sourceJob;
    @Column(name = "input_snapshot_hash", length = 64, nullable = false, updatable = false)
    private String inputSnapshotHash;
    @Column(name = "source_version", length = 100, nullable = false, updatable = false)
    private String sourceVersion;
    @Column(name = "prompt_version", length = 100, nullable = false, updatable = false)
    private String promptVersion;
    @Column(name = "schema_version", length = 100, nullable = false, updatable = false)
    private String schemaVersion;
    @Column(name = "model_id", length = 100, nullable = false, updatable = false) private String modelId;
    @Column(name = "section_count", nullable = false, updatable = false) private int sectionCount;
    @Column(name = "finding_count", nullable = false, updatable = false) private int findingCount;

    @Builder
    private ProjectFlowCheck(Project project, AiJob sourceJob, String inputSnapshotHash,
                             String sourceVersion, String promptVersion, String schemaVersion,
                             String modelId, int sectionCount, int findingCount) {
        this.project = Objects.requireNonNull(project, "project는 필수입니다.");
        this.sourceJob = Objects.requireNonNull(sourceJob, "sourceJob은 필수입니다.");
        if (inputSnapshotHash == null || !SHA_256.matcher(inputSnapshotHash).matches()
                || sectionCount <= 0 || findingCount < 0) {
            throw new IllegalArgumentException("flow check snapshot/count가 유효하지 않습니다.");
        }
        this.inputSnapshotHash = inputSnapshotHash;
        this.sourceVersion = text(sourceVersion);
        this.promptVersion = text(promptVersion);
        this.schemaVersion = text(schemaVersion);
        this.modelId = text(modelId);
        this.sectionCount = sectionCount;
        this.findingCount = findingCount;
    }

    private String text(String value) {
        if (value == null || value.isBlank() || value.length() > 100) {
            throw new IllegalArgumentException("flow check version/model이 유효하지 않습니다.");
        }
        return value;
    }
}
