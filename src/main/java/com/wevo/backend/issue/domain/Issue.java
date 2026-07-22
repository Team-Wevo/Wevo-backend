package com.wevo.backend.issue.domain;

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
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 정리 세트에 속한 충돌(CONFLICT) 또는 정보 공백(GAP) 쟁점. */
@Entity
@Table(name = "issues", uniqueConstraints = @UniqueConstraint(
        name = "uk_issues_set_order", columnNames = {"synthesis_set_id", "sort_order"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Issue extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "synthesis_set_id", nullable = false, updatable = false)
    private SynthesisSet synthesisSet;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false, updatable = false)
    private IssueType type;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private IssueStatus status;

    @Column(columnDefinition = "TEXT", nullable = false, updatable = false)
    private String description;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String question;

    @Column(name = "sort_order", nullable = false, updatable = false)
    private int sortOrder;

    @Builder
    private Issue(SynthesisSet synthesisSet, IssueType type, String description,
                  String question, int sortOrder) {
        this.synthesisSet = Objects.requireNonNull(synthesisSet, "synthesisSet은 필수입니다.");
        this.type = Objects.requireNonNull(type, "type은 필수입니다.");
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description은 필수입니다.");
        }
        if (type == IssueType.CONFLICT && (question == null || question.isBlank())) {
            throw new IllegalArgumentException("CONFLICT 쟁점에는 question이 필요합니다.");
        }
        if (type == IssueType.GAP && question != null) {
            throw new IllegalArgumentException("GAP 쟁점에는 question을 저장할 수 없습니다.");
        }
        if (sortOrder <= 0) {
            throw new IllegalArgumentException("sortOrder는 1 이상이어야 합니다.");
        }
        this.status = IssueStatus.PENDING;
        this.description = description;
        this.question = question;
        this.sortOrder = sortOrder;
    }

    public void resolve() {
        this.status = IssueStatus.RESOLVED;
    }
}
