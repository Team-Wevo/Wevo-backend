package com.wevo.backend.ai.domain;

import com.wevo.backend.global.common.BaseTimeEntity;
import com.wevo.backend.section.domain.ProjectSection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 특정 draft/dependency snapshot에 귀속된 성공한 AI 사전 검토 결과. */
@Entity
@Table(name = "ai_section_checks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiSectionCheck extends BaseTimeEntity {

    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_section_id", nullable = false)
    private ProjectSection projectSection;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_job_id", nullable = false, unique = true)
    private AiJob sourceJob;

    @Column(name = "checked_draft_id", nullable = false)
    private Long checkedDraftId;

    @Column(name = "checked_content_version", nullable = false)
    private Integer checkedContentVersion;

    @Column(name = "input_snapshot_hash", nullable = false, length = 64)
    private String inputSnapshotHash;

    @Column(name = "source_version", nullable = false, length = 100)
    private String sourceVersion;

    @Column(name = "dependency_version_hash", nullable = false, length = 16)
    private String dependencyVersionHash;

    @Column(name = "rewrite_content", nullable = false, length = 10_000)
    private String rewriteContent;

    @Column(name = "changed_count", nullable = false)
    private Integer changedCount;

    @Column(name = "rewrite_applied", nullable = false)
    private boolean rewriteApplied;

    @Column(name = "applied_content_version")
    private Integer appliedContentVersion;

    @Builder
    private AiSectionCheck(
            ProjectSection projectSection,
            AiJob sourceJob,
            Long checkedDraftId,
            Integer checkedContentVersion,
            String inputSnapshotHash,
            String sourceVersion,
            String dependencyVersionHash,
            String rewriteContent,
            Integer changedCount
    ) {
        this.projectSection = Objects.requireNonNull(projectSection, "section은 필수입니다.");
        this.sourceJob = Objects.requireNonNull(sourceJob, "sourceJob은 필수입니다.");
        this.checkedDraftId = requirePositive(checkedDraftId, "checkedDraftId");
        this.checkedContentVersion =
                requirePositive(checkedContentVersion, "checkedContentVersion");
        this.inputSnapshotHash = requireHash(inputSnapshotHash);
        this.sourceVersion = requireText(sourceVersion, "sourceVersion", 100);
        this.dependencyVersionHash =
                requireText(dependencyVersionHash, "dependencyVersionHash", 16);
        this.rewriteContent = requireText(rewriteContent, "rewriteContent", 10_000);
        if (changedCount == null || changedCount < 0) {
            throw new IllegalArgumentException("changedCount는 0 이상이어야 합니다.");
        }
        this.changedCount = changedCount;
        this.rewriteApplied = false;
    }

    /** 적용된 rewrite draft로 검토 기준을 한 번만 재바인딩한다. */
    public void bindAppliedRewrite(Long draftId, int contentVersion) {
        if (rewriteApplied) {
            throw new IllegalStateException("이미 적용된 rewrite입니다.");
        }
        this.checkedDraftId = requirePositive(draftId, "draftId");
        this.checkedContentVersion = requirePositive(contentVersion, "contentVersion");
        this.rewriteApplied = true;
        this.appliedContentVersion = contentVersion;
    }

    private static <T extends Number> T requirePositive(T value, String field) {
        if (value == null || value.longValue() <= 0) {
            throw new IllegalArgumentException(field + "는 1 이상이어야 합니다.");
        }
        return value;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + "는 공백이 아니며 " + maxLength + "자 이하여야 합니다.");
        }
        return value;
    }

    private static String requireHash(String value) {
        if (value == null || !SHA_256_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("inputSnapshotHash가 유효하지 않습니다.");
        }
        return value;
    }
}
