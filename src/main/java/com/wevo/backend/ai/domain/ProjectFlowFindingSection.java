package com.wevo.backend.ai.domain;

import com.wevo.backend.ai.service.ProjectFlowReviewContract;
import com.wevo.backend.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "project_flow_finding_sections", uniqueConstraints = {
        @UniqueConstraint(name = "uk_project_flow_finding_section", columnNames = {"finding_id", "project_section_id"}),
        @UniqueConstraint(name = "uk_project_flow_finding_order", columnNames = {"finding_id", "sort_order"})})
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectFlowFindingSection extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "finding_id", nullable = false, updatable = false) private ProjectFlowCheckFinding finding;
    @Column(name = "project_section_id", nullable = false, updatable = false) private Long projectSectionId;
    @Column(name = "confirmed_version", nullable = false, updatable = false) private int confirmedVersion;
    @Column(name = "target_excerpt", length = ProjectFlowReviewContract.MAX_EXCERPT_LENGTH, nullable = false, updatable = false)
    private String targetExcerpt;
    @Column(name = "sort_order", nullable = false, updatable = false) private int sortOrder;

    @Builder
    private ProjectFlowFindingSection(ProjectFlowCheckFinding finding, Long projectSectionId,
                                      int confirmedVersion, String targetExcerpt, int sortOrder) {
        if (finding == null || projectSectionId == null || projectSectionId <= 0 || confirmedVersion <= 0 || sortOrder <= 0
                || targetExcerpt == null || targetExcerpt.isBlank()
                || targetExcerpt.length() > ProjectFlowReviewContract.MAX_EXCERPT_LENGTH) {
            throw new IllegalArgumentException("finding section이 유효하지 않습니다.");
        }
        this.finding = finding; this.projectSectionId = projectSectionId;
        this.confirmedVersion = confirmedVersion; this.targetExcerpt = targetExcerpt; this.sortOrder = sortOrder;
    }
}
