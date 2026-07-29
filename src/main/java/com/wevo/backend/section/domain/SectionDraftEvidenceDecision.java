package com.wevo.backend.section.domain;

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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** AI 초안 생성 시 반영한 CONFLICT 결정 스냅샷. */
@Entity
@Table(name = "section_draft_evidence_decisions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_draft_evidence_decision_issue",
                columnNames = {"draft_evidence_id", "issue_id"}),
        @UniqueConstraint(name = "uk_draft_evidence_decision_order",
                columnNames = {"draft_evidence_id", "sort_order"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SectionDraftEvidenceDecision extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "draft_evidence_id", nullable = false, updatable = false)
    private SectionDraftEvidence draftEvidence;

    @Column(name = "issue_id", nullable = false, updatable = false)
    private Long issueId;

    @Column(name = "decision_id", nullable = false, updatable = false)
    private Long decisionId;

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String decision;

    @Column(name = "sort_order", nullable = false, updatable = false)
    private int sortOrder;

    @Builder
    private SectionDraftEvidenceDecision(
            SectionDraftEvidence draftEvidence,
            Long issueId,
            Long decisionId,
            String question,
            String decision,
            int sortOrder
    ) {
        this.draftEvidence = Objects.requireNonNull(draftEvidence, "draftEvidence는 필수입니다.");
        this.issueId = Objects.requireNonNull(issueId, "issueId는 필수입니다.");
        this.decisionId = Objects.requireNonNull(decisionId, "decisionId는 필수입니다.");
        this.question = requireText(question, "question");
        this.decision = requireText(decision, "decision");
        if (sortOrder <= 0) {
            throw new IllegalArgumentException("sortOrder는 1 이상이어야 합니다.");
        }
        this.sortOrder = sortOrder;
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "는 필수입니다.");
        }
        return value;
    }
}
