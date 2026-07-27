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

/** CONFLICT 쟁점에 대해 AI가 제시한 선택지. */
@Entity
@Table(name = "issue_options", uniqueConstraints = {
        @UniqueConstraint(name = "uk_issue_options_issue_order", columnNames = {"issue_id", "sort_order"}),
        @UniqueConstraint(name = "uk_issue_options_issue_text", columnNames = {"issue_id", "option_text"}),
        @UniqueConstraint(name = "uk_issue_options_id_issue", columnNames = {"id", "issue_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssueOption extends BaseTimeEntity {

    public static final int MAX_OPTION_TEXT_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_id", nullable = false, updatable = false)
    private Issue issue;

    @Column(name = "option_text", columnDefinition = "TEXT", nullable = false, updatable = false)
    private String optionText;

    @Column(name = "sort_order", nullable = false, updatable = false)
    private int sortOrder;

    @Builder
    private IssueOption(Issue issue, String optionText, int sortOrder) {
        this.issue = requireConflict(issue);
        if (optionText == null || optionText.isBlank()) {
            throw new IllegalArgumentException("optionText는 필수입니다.");
        }
        if (optionText.length() > MAX_OPTION_TEXT_LENGTH) {
            throw new IllegalArgumentException("optionText는 200자 이하여야 합니다.");
        }
        if (sortOrder <= 0) {
            throw new IllegalArgumentException("sortOrder는 1 이상이어야 합니다.");
        }
        this.optionText = optionText;
        this.sortOrder = sortOrder;
    }

    private Issue requireConflict(Issue issue) {
        Objects.requireNonNull(issue, "issue는 필수입니다.");
        if (issue.getType() != IssueType.CONFLICT) {
            throw new IllegalArgumentException("선택지는 CONFLICT 쟁점에만 저장할 수 있습니다.");
        }
        return issue;
    }
}
