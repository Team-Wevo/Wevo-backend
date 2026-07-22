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

/** OWNER가 GAP 관련 의견 작성자에게 보낸 1회성 보충 근거 요청. */
@Entity
@Table(name = "evidence_requests", uniqueConstraints = @UniqueConstraint(
        name = "uk_evidence_requests_issue", columnNames = "issue_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvidenceRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_id", nullable = false, updatable = false)
    private Issue issue;

    @Column(name = "requested_by_user_id", nullable = false, updatable = false)
    private Long requestedByUserId;

    @Column(name = "target_user_id", nullable = false, updatable = false)
    private Long targetUserId;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Builder
    private EvidenceRequest(Issue issue, Long requestedByUserId,
                            Long targetUserId, LocalDateTime requestedAt) {
        Objects.requireNonNull(issue, "issue는 필수입니다.");
        if (issue.getType() != IssueType.GAP) {
            throw new IllegalArgumentException("추가 근거 요청은 GAP 쟁점에만 저장할 수 있습니다.");
        }
        this.issue = issue;
        this.requestedByUserId = Objects.requireNonNull(requestedByUserId, "requestedByUserId는 필수입니다.");
        this.targetUserId = Objects.requireNonNull(targetUserId, "targetUserId는 필수입니다.");
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt은 필수입니다.");
    }
}
