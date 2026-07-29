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
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** AI 초안 생성 시 사용한 직접·승계 GAP 답변 스냅샷. */
@Entity
@Table(name = "section_draft_evidence_gap_answers", uniqueConstraints = {
        @UniqueConstraint(name = "uk_draft_evidence_gap_answer",
                columnNames = {"draft_evidence_id", "answer_id"}),
        @UniqueConstraint(name = "uk_draft_evidence_gap_order",
                columnNames = {"draft_evidence_id", "sort_order"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SectionDraftEvidenceGapAnswer extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "draft_evidence_id", nullable = false, updatable = false)
    private SectionDraftEvidence draftEvidence;

    @Column(name = "source_issue_id", nullable = false, updatable = false)
    private Long sourceIssueId;

    @Column(name = "answer_id", nullable = false, updatable = false)
    private Long answerId;

    @Column(name = "author_name_snapshot", nullable = false, length = 100, updatable = false)
    private String authorNameSnapshot;

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String content;

    @Column(name = "answered_at", nullable = false, updatable = false)
    private LocalDateTime answeredAt;

    @Column(nullable = false, updatable = false)
    private boolean inherited;

    @Column(name = "sort_order", nullable = false, updatable = false)
    private int sortOrder;

    @Builder
    private SectionDraftEvidenceGapAnswer(
            SectionDraftEvidence draftEvidence,
            Long sourceIssueId,
            Long answerId,
            String authorNameSnapshot,
            String content,
            LocalDateTime answeredAt,
            boolean inherited,
            int sortOrder
    ) {
        this.draftEvidence = Objects.requireNonNull(draftEvidence, "draftEvidence는 필수입니다.");
        this.sourceIssueId = Objects.requireNonNull(sourceIssueId, "sourceIssueId는 필수입니다.");
        this.answerId = Objects.requireNonNull(answerId, "answerId는 필수입니다.");
        this.authorNameSnapshot = requireText(authorNameSnapshot, "authorNameSnapshot");
        this.content = requireText(content, "content");
        this.answeredAt = Objects.requireNonNull(answeredAt, "answeredAt는 필수입니다.");
        this.inherited = inherited;
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
