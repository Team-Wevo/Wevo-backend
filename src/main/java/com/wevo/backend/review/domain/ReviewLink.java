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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 섹션 외부 검토용 공유 링크. 외부 검토자에게 토큰으로 발급된다.
 *
 * <p>내부(팀) 검토는 멤버십 기반(§6.1)이라 링크를 쓰지 않으므로, 이 링크는 외부 검토 전용이다.
 *
 * <h2>보안 · 남용 방지</h2>
 * <ul>
 *   <li><b>토큰 해시 저장</b> — 원문 토큰은 URL 로만 전달하고 DB 에는 SHA-256 해시({@code tokenHash})만 저장한다.
 *       조회 시 요청 토큰을 해시해 비교한다. (DB 유출 시에도 링크 접근 불가)</li>
 *   <li><b>본문 스냅샷 (발급 시점 버전 고정)</b> — 링크는 발급 시점의 <b>최신 초안 버전</b>에 고정되며,
 *       제목·본문·{@code contentVersion} 스냅샷은 발급 이후 <b>불변</b>이다. 외부 검토자는 <b>항상 스냅샷</b>을
 *       읽으며, 이후 본문이 수정돼도 링크가 가리키던 버전을 그대로 본다. 초안이 없으면 발급을 거부한다(R009).
 *       새 본문에 대한 외부 검토는 링크 <b>재발급</b>으로만 가능하다.</li>
 *   <li><b>버전 만료</b> — 본문이 수정되면 링크는 {@link ReviewLinkStatus#OUTDATED} 로 만료되고,
 *       팀장은 {@link ReviewLinkStatus#CLOSED} 로 직접 비활성화할 수 있다. 만료·비활성 링크는 제출을 받지 않는다.</li>
 * </ul>
 */
@Entity
@Table(name = "review_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewLink extends BaseTimeEntity {

    /** 링크 하나가 받을 수 있는 외부 검토 제출 상한. (스팸 최종 방어선) */
    public static final int MAX_SUBMISSIONS = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_section_id", nullable = false)
    private ProjectSection projectSection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    /** 원문 토큰의 SHA-256 해시. 원문은 저장하지 않는다. */
    @Column(name = "token_hash", length = 64, unique = true, nullable = false)
    private String tokenHash;

    /** 발급 시점 섹션 제목 스냅샷. */
    @Column(name = "section_title_snapshot", length = 200)
    private String sectionTitleSnapshot;

    /** 발급 시점 본문 스냅샷. 외부 검토자는 항상 이 값을 읽는다. (발급 이후 불변) */
    @Column(name = "content_snapshot", columnDefinition = "TEXT", nullable = false)
    private String contentSnapshot;

    /**
     * 링크가 고정하는 발급 시점 본문 버전. (발급 이후 불변 — 만료 판단·추적용)
     *
     * <p>초안 없는 발급은 거부되므로(R009) 항상 존재하며, 이 링크의 모든 제출은
     * 이 버전을 검토한 것으로 해석한다.
     */
    @Column(name = "content_version", nullable = false)
    private Integer contentVersion;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private ReviewLinkStatus status;

    @Builder
    private ReviewLink(ProjectSection projectSection, User createdBy, String tokenHash,
                       String sectionTitleSnapshot, String contentSnapshot, Integer contentVersion,
                       ReviewLinkStatus status) {
        this.projectSection = projectSection;
        this.createdBy = createdBy;
        this.tokenHash = tokenHash;
        this.sectionTitleSnapshot = sectionTitleSnapshot;
        this.contentSnapshot = contentSnapshot;
        this.contentVersion = contentVersion;
        this.status = status;
    }

    /** 열람·제출을 받는 정상 상태인지. */
    public boolean isActive() {
        return status == ReviewLinkStatus.ACTIVE;
    }

    /**
     * 본문이 수정돼 링크를 만료시킨다. (첫 실제 저장 시점에 호출)
     * 이미 만료·비활성인 링크는 상태를 유지한다.
     */
    public void markOutdated() {
        if (status == ReviewLinkStatus.ACTIVE) {
            this.status = ReviewLinkStatus.OUTDATED;
        }
    }

    /** 팀장이 링크를 직접 비활성화한다. */
    public void close() {
        this.status = ReviewLinkStatus.CLOSED;
    }
}
