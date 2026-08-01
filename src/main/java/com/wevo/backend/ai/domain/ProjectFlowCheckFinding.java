package com.wevo.backend.ai.domain;

import com.wevo.backend.ai.service.ProjectFlowReviewContract;
import com.wevo.backend.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "project_flow_check_findings", uniqueConstraints =
        @UniqueConstraint(name = "uk_project_flow_findings_check_order", columnNames = {"flow_check_id", "sort_order"}))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectFlowCheckFinding extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flow_check_id", nullable = false, updatable = false) private ProjectFlowCheck flowCheck;
    @Enumerated(EnumType.STRING) @Column(length = 60, nullable = false, updatable = false)
    private ProjectFlowFindingType type;
    @Column(length = ProjectFlowReviewContract.MAX_DESCRIPTION_LENGTH, nullable = false, updatable = false)
    private String description;
    @Column(length = ProjectFlowReviewContract.MAX_SUGGESTION_LENGTH, nullable = false, updatable = false)
    private String suggestion;
    @Column(name = "sort_order", nullable = false, updatable = false) private int sortOrder;

    @Builder
    private ProjectFlowCheckFinding(ProjectFlowCheck flowCheck, ProjectFlowFindingType type,
                                    String description, String suggestion, int sortOrder) {
        if (flowCheck == null || type == null || sortOrder <= 0) throw new IllegalArgumentException("finding 식별값은 필수입니다.");
        this.flowCheck = flowCheck; this.type = type; this.sortOrder = sortOrder;
        this.description = text(description, ProjectFlowReviewContract.MAX_DESCRIPTION_LENGTH);
        this.suggestion = text(suggestion, ProjectFlowReviewContract.MAX_SUGGESTION_LENGTH);
    }
    private String text(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max) throw new IllegalArgumentException("finding 문자열이 유효하지 않습니다.");
        return value;
    }
}
