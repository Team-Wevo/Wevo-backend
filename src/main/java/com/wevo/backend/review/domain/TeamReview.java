package com.wevo.backend.review.domain;

import com.wevo.backend.global.common.BaseTimeEntity;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.user.domain.User;
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

/**
 * 팀(내부) 검토 — 팀원(MEMBER)이 섹션 초안에 동의/수정요청을 남기는 의사결정. (제품 정책서 §6.1)
 *
 * <p>팀(내부) 검토는 "팀의 합의"다.
 * 그래서 enum({@link TeamReviewStatus})과 테이블을 외부 검토와 분리한다.
 *
 * <ul>
 *   <li><b>1인 1검토</b> — {@code (project_section_id, reviewer_user_id)} 유니크 제약.</li>
 *   <li><b>팀장 제외</b> — 검토자는 팀원(MEMBER)만. 팀장(OWNER)은 검토를 제출하지 않는다.</li>
 *   <li>{@code reviewedContentVersion} — 어느 본문 버전을 보고 검토했는지 기록. (드리프트/재검토 판단 근거)</li>
 * </ul>
 */
@Entity
@Table( name = "team_reviews",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_team_review_section_reviewer",
                columnNames = {"project_section_id", "reviewer_user_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamReview extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_section_id")
    private ProjectSection projectSection;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_user_id")
    private User reviewer;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private TeamReviewStatus status;

    /** 검토 시점의 본문 버전 */
    @Column(name = "reviewed_content_version")
    private Integer reviewedContentVersion;

    /** 수정 요청 사유. CHANGES_REQUESTED 일 때만 작성*/
    @Column(name = "change_request_reason", columnDefinition = "TEXT")
    private String changeRequestReason;

    /** 팀장이 "수정 안 하고 합의됨"으로 처리하면 true. (§6.1.1) */
    @Column(nullable = false)
    private boolean resolved;

    @Column(nullable = false)
    private boolean outdated;

    @Builder
    private TeamReview(ProjectSection projectSection, User reviewer, TeamReviewStatus status,
                       Integer reviewedContentVersion, String changeRequestReason) {
        this.projectSection = projectSection;
        this.reviewer = reviewer;
        this.status = status;
        this.reviewedContentVersion = reviewedContentVersion;
        this.changeRequestReason = changeRequestReason;
        this.resolved = false;
        this.outdated = false;
    }

    /**
     * 검토 내용을 갱신한다. (같은 팀원이 다시 제출 시)
     * 새 본문 기준의 새 검토이므로 {@code resolved}·{@code outdated} 를 모두 초기화한다.
     */
    public void apply(TeamReviewStatus status, String changeRequestReason, Integer reviewedContentVersion) {
        this.status = status;
        this.changeRequestReason = changeRequestReason;
        this.reviewedContentVersion = reviewedContentVersion;
        this.resolved = false;
        this.outdated = false;
    }

    /** 본문 수정으로 이 검토를 만료 처리한다.*/
    public void markOutdated() {
        this.outdated = true;
    }

    /** 수정 요청 해소 여부를 변경한다. (팀장 전용) */
    public void updateResolved(boolean resolved) {
        this.resolved = resolved;
    }
}
