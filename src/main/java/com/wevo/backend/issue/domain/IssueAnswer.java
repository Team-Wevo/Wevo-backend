package com.wevo.backend.issue.domain;

import com.wevo.backend.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 지목된 팀원이 GAP 추가 근거 요청에 남긴 단일 답변. */
@Entity
@Table(name = "issue_answers", uniqueConstraints = {
        @UniqueConstraint(name = "uk_issue_answers_issue", columnNames = "issue_id"),
        @UniqueConstraint(name = "uk_issue_answers_evidence_request", columnNames = "evidence_request_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssueAnswer extends BaseTimeEntity {

    public static final int MAX_CONTENT_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_id", nullable = false, updatable = false)
    private Issue issue;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evidence_request_id", nullable = false, updatable = false)
    private EvidenceRequest evidenceRequest;

    @Column(name = "author_user_id", nullable = false, updatable = false)
    private Long authorUserId;

    @Column(name = "author_name_snapshot", length = 100, nullable = false, updatable = false)
    private String authorNameSnapshot;

    @Column(length = MAX_CONTENT_LENGTH, nullable = false, updatable = false)
    private String content;

    @Column(name = "answered_at", nullable = false, updatable = false)
    private LocalDateTime answeredAt;

    @Builder
    private IssueAnswer(EvidenceRequest evidenceRequest, Long authorUserId,
                        String authorNameSnapshot, String content, LocalDateTime answeredAt) {
        this.evidenceRequest = Objects.requireNonNull(evidenceRequest, "evidenceRequest는 필수입니다.");
        this.issue = evidenceRequest.getIssue();
        this.authorUserId = Objects.requireNonNull(authorUserId, "authorUserId는 필수입니다.");
        if (!authorUserId.equals(evidenceRequest.getTargetUserId())) {
            throw new IllegalArgumentException("답변자는 근거 요청에 지목된 사용자여야 합니다.");
        }
        if (authorNameSnapshot == null || authorNameSnapshot.isBlank()) {
            throw new IllegalArgumentException("authorNameSnapshot은 필수입니다.");
        }
        if (content == null || content.isBlank() || content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("content는 공백이 아닌 1000자 이하여야 합니다.");
        }
        this.authorNameSnapshot = authorNameSnapshot;
        this.content = content;
        this.answeredAt = Objects.requireNonNull(answeredAt, "answeredAt은 필수입니다.");
    }
}
