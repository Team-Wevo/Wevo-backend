package com.wevo.backend.opinion.domain;

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
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 팀원이 섹션에 작성한 의견. AI가 이들을 모아 합의점·쟁점을 정리한다. (제품 정책서 §4·§5)
 *
 * <p>한 멤버는 한 섹션에 <b>하나의 의견</b>만 가진다(유니크 제약). 그래서 별도 생성 API 없이
 * 임시저장(upsert)이 생성을 겸하고, 삭제 대신 수정으로 갈음하는 것이 기본 정책이다.
 *
 * <p><b>재제출 모델(§4.1)</b> — 하나의 의견이 두 본문을 가진다.
 * {@code content}는 임시저장으로 갱신되는 <b>작업본</b>, {@code submittedContent}는 제출 시점에만
 * 갱신되는 <b>제출본</b>이다. 제출 후 재편집해도 팀에 공개되는 제출본은 유지되고,
 * 재제출해야 제출본이 최신 작업본으로 갱신된다.
 */
@Entity
@Table(name = "opinions", uniqueConstraints = @UniqueConstraint(
        name = "uk_opinions_section_author",
        columnNames = {"project_section_id", "author_user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Opinion extends BaseTimeEntity {

    public static final int MIN_CONTENT_LENGTH = 20;
    public static final int MAX_CONTENT_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_section_id")
    private ProjectSection projectSection;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_user_id")
    private User author;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "submitted_content", columnDefinition = "TEXT")
    private String submittedContent;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private OpinionStatus status;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Builder
    private Opinion(ProjectSection projectSection, User author, String content, OpinionStatus status,
                    LocalDateTime submittedAt) {
        this.projectSection = projectSection;
        this.author = author;
        this.content = content;
        this.status = status;
        this.submittedAt = submittedAt;
    }

    /**
     * 작업본을 덮어쓴다. 제출본({@code submittedContent})과 상태는 바꾸지 않는다 —
     * 제출 후 재편집 중에도 팀에는 기존 제출본이 유지된다. (§4.1)
     */
    public void updateContent(String content) {
        this.content = content;
    }

    /**
     * 현재 작업본을 제출본으로 반영한다. 재제출이면 제출본만 갱신한다. (§4.1)
     *
     * <p>{@code submittedAt}은 <b>최초 제출 시각</b>이다 — 재제출해도 갱신하지 않는다(§4.3 제출 이력).
     * 작업본이 제출본과 같으면 아무것도 바꾸지 않는다(멱등).
     */
    public void submit(LocalDateTime submittedAt) {
        if (status == OpinionStatus.SUBMITTED && !hasUnsubmittedChanges()) {
            return;
        }
        if (status != OpinionStatus.SUBMITTED) {
            this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt must not be null");
            this.status = OpinionStatus.SUBMITTED;
        }
        this.submittedContent = content;
    }

    /**
     * 제출 후 재편집으로 작업본이 제출본과 달라졌는지. (재제출 필요 여부 힌트 — API_SPEC §3.4.1)
     */
    public boolean hasUnsubmittedChanges() {
        return status == OpinionStatus.SUBMITTED && !content.equals(getSubmittedContentOrLegacy());
    }

    /**
     * 팀에 공개되는 제출본. 재제출 모델 도입 전에 제출된 레거시 행은 제출본 컬럼이 비어 있으므로
     * 당시 의미(작업본 = 제출본)에 맞게 작업본으로 대체한다.
     */
    public String getSubmittedContentOrLegacy() {
        return submittedContent != null ? submittedContent : content;
    }
}
