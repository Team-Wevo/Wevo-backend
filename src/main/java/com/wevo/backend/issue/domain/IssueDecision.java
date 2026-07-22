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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** OWNER가 CONFLICT 쟁점에 남긴 선택지 또는 직접 입력 결정. */
@Entity
@Table(name = "issue_decisions", uniqueConstraints = @UniqueConstraint(
        name = "uk_issue_decisions_issue", columnNames = "issue_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssueDecision extends BaseTimeEntity {

    public static final int MAX_CUSTOM_INPUT_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_id", nullable = false, updatable = false)
    private Issue issue;

    @Column(name = "decided_by_user_id", nullable = false, updatable = false)
    private Long decidedByUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_option_id", updatable = false)
    private IssueOption selectedOption;

    @Column(name = "custom_input", length = MAX_CUSTOM_INPUT_LENGTH, updatable = false)
    private String customInput;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private LocalDateTime decidedAt;

    private IssueDecision(Issue issue, Long decidedByUserId, IssueOption selectedOption,
                          String customInput, LocalDateTime decidedAt) {
        this.issue = requireConflict(issue);
        this.decidedByUserId = Objects.requireNonNull(decidedByUserId, "decidedByUserId는 필수입니다.");
        validateChoice(issue, selectedOption, customInput);
        this.selectedOption = selectedOption;
        this.customInput = customInput;
        this.decidedAt = Objects.requireNonNull(decidedAt, "decidedAt은 필수입니다.");
    }

    public static IssueDecision select(Issue issue, Long decidedByUserId,
                                       IssueOption selectedOption, LocalDateTime decidedAt) {
        return new IssueDecision(issue, decidedByUserId,
                Objects.requireNonNull(selectedOption, "selectedOption은 필수입니다."), null, decidedAt);
    }

    public static IssueDecision custom(Issue issue, Long decidedByUserId,
                                       String customInput, LocalDateTime decidedAt) {
        return new IssueDecision(issue, decidedByUserId, null, customInput, decidedAt);
    }

    private Issue requireConflict(Issue issue) {
        Objects.requireNonNull(issue, "issue는 필수입니다.");
        if (issue.getType() != IssueType.CONFLICT) {
            throw new IllegalArgumentException("결정은 CONFLICT 쟁점에만 저장할 수 있습니다.");
        }
        return issue;
    }

    private void validateChoice(Issue issue, IssueOption selectedOption, String customInput) {
        boolean hasSelectedOption = selectedOption != null;
        boolean hasCustomInput = customInput != null && !customInput.isBlank();
        if (hasSelectedOption == hasCustomInput) {
            throw new IllegalArgumentException("selectedOption과 customInput 중 하나만 지정해야 합니다.");
        }
        if (hasSelectedOption && !belongsTo(selectedOption, issue)) {
            throw new IllegalArgumentException("선택지는 결정 대상 쟁점에 속해야 합니다.");
        }
        if (hasCustomInput && customInput.length() > MAX_CUSTOM_INPUT_LENGTH) {
            throw new IllegalArgumentException("customInput은 200자 이하여야 합니다.");
        }
    }

    private boolean belongsTo(IssueOption selectedOption, Issue issue) {
        Issue optionIssue = selectedOption.getIssue();
        if (optionIssue == issue) {
            return true;
        }
        return optionIssue.getId() != null && issue.getId() != null
                && optionIssue.getId().equals(issue.getId());
    }
}
