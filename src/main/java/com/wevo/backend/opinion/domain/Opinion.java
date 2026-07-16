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
 * 팀원이 섹션에 작성한 의견. AI가 이들을 모아 합의점·쟁점을 정리한다. (제품 정책서 §5)
 *
 * <p>한 멤버는 한 섹션에 <b>하나의 의견</b>만 가진다(유니크 제약). 그래서 별도 생성 API 없이
 * 임시저장(upsert)이 생성을 겸하고, 삭제 대신 수정으로 갈음하는 것이 기본 정책이다.
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
     * 본문을 덮어쓴다. 상태는 바꾸지 않는다 — SUBMITTED 의견을 수정해도 SUBMITTED 로 유지된다
     * (재제출 절차 없음, 정책서 §5 "삭제 미지원 — 수정으로 갈음").
     */
    public void updateContent(String content) {
        this.content = content;
    }

    /**
     * 의견을 최초 제출 상태로 전환한다.
     *
     * <p>이미 제출된 의견에 다시 호출해도 최초 제출 시각을 유지한다.
     */
    public void submit(LocalDateTime submittedAt) {
        if (status == OpinionStatus.SUBMITTED) {
            return;
        }
        LocalDateTime firstSubmittedAt = Objects.requireNonNull(
                submittedAt,
                "submittedAt must not be null"
        );
        this.status = OpinionStatus.SUBMITTED;
        this.submittedAt = firstSubmittedAt;
    }
}
