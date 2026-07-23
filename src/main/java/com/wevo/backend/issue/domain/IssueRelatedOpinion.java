package com.wevo.backend.issue.domain;

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

/** 쟁점의 근거가 된 제출 의견과 생성 당시 표시 스냅샷. */
@Entity
@Table(name = "issue_related_opinions", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_issue_related_opinions_issue_opinion",
                columnNames = {"issue_id", "opinion_id"}),
        @UniqueConstraint(
                name = "uk_issue_related_opinions_issue_order",
                columnNames = {"issue_id", "sort_order"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssueRelatedOpinion extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_id", nullable = false, updatable = false)
    private Issue issue;

    @Column(name = "opinion_id", nullable = false, updatable = false)
    private Long opinionId;

    @Column(name = "author_user_id", nullable = false, updatable = false)
    private Long authorUserId;

    @Column(name = "author_name_snapshot", length = 100, nullable = false, updatable = false)
    private String authorNameSnapshot;

    @Column(columnDefinition = "TEXT", nullable = false, updatable = false)
    private String excerpt;

    @Column(name = "sort_order", nullable = false, updatable = false)
    private int sortOrder;

    @Builder
    private IssueRelatedOpinion(Issue issue, Long opinionId, Long authorUserId,
                                String authorNameSnapshot, String excerpt, int sortOrder) {
        this.issue = Objects.requireNonNull(issue, "issue는 필수입니다.");
        this.opinionId = Objects.requireNonNull(opinionId, "opinionId는 필수입니다.");
        this.authorUserId = Objects.requireNonNull(authorUserId, "authorUserId는 필수입니다.");
        if (authorNameSnapshot == null || authorNameSnapshot.isBlank()) {
            throw new IllegalArgumentException("authorNameSnapshot은 필수입니다.");
        }
        if (excerpt == null || excerpt.isBlank()) {
            throw new IllegalArgumentException("excerpt는 필수입니다.");
        }
        if (sortOrder <= 0) {
            throw new IllegalArgumentException("sortOrder는 1 이상이어야 합니다.");
        }
        this.authorNameSnapshot = authorNameSnapshot;
        this.excerpt = excerpt;
        this.sortOrder = sortOrder;
    }
}
