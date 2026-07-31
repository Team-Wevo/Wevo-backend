package com.wevo.backend.section.domain;

import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.global.common.BaseTimeEntity;
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
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 섹션 초안 버전에 귀속된 AI 제안과 사람 확정 의도. */
@Entity
@Table(
        name = "section_author_intents",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_section_author_intents_version",
                columnNames = {"project_section_id", "content_version"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SectionAuthorIntent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_section_id", nullable = false)
    private ProjectSection projectSection;

    @Column(name = "content_version", nullable = false)
    private Integer contentVersion;

    @Column(name = "ai_suggested_intent", length = AuthorIntentTextPolicy.MAX_LENGTH)
    private String aiSuggestedIntent;

    @Column(name = "confirmed_intent", length = AuthorIntentTextPolicy.MAX_LENGTH)
    private String confirmedIntent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SectionAuthorIntentStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_ai_job_id")
    private AiJob sourceAiJob;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by_user_id")
    private User confirmedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    private SectionAuthorIntent(ProjectSection section, int contentVersion) {
        this.projectSection = Objects.requireNonNull(section, "section은 필수입니다.");
        if (contentVersion <= 0) {
            throw new IllegalArgumentException("contentVersion은 1 이상이어야 합니다.");
        }
        this.contentVersion = contentVersion;
    }

    public static SectionAuthorIntent suggested(
            ProjectSection section,
            int contentVersion,
            String suggestion,
            AiJob sourceJob
    ) {
        SectionAuthorIntent intent = new SectionAuthorIntent(section, contentVersion);
        intent.updateSuggestion(suggestion, sourceJob);
        return intent;
    }

    public static SectionAuthorIntent confirmed(
            ProjectSection section,
            int contentVersion,
            String finalIntent,
            User confirmer,
            LocalDateTime confirmedAt
    ) {
        SectionAuthorIntent intent = new SectionAuthorIntent(section, contentVersion);
        intent.confirm(finalIntent, confirmer, confirmedAt);
        return intent;
    }

    /** 새 AI 제안은 보존하되 이미 확정한 사람의 값을 덮어쓰지 않는다. */
    public void updateSuggestion(String suggestion, AiJob sourceJob) {
        this.aiSuggestedIntent = requireIntent(suggestion);
        this.sourceAiJob = Objects.requireNonNull(sourceJob, "sourceJob은 필수입니다.");
        if (status != SectionAuthorIntentStatus.CONFIRMED) {
            this.status = SectionAuthorIntentStatus.SUGGESTED;
        }
    }

    public void confirm(String finalIntent, User confirmer, LocalDateTime confirmedAt) {
        this.confirmedIntent = requireIntent(finalIntent);
        this.confirmedBy = Objects.requireNonNull(confirmer, "confirmer는 필수입니다.");
        this.confirmedAt = Objects.requireNonNull(confirmedAt, "confirmedAt은 필수입니다.");
        this.status = SectionAuthorIntentStatus.CONFIRMED;
    }

    public boolean isConfirmedAs(String intent) {
        return status == SectionAuthorIntentStatus.CONFIRMED
                && Objects.equals(confirmedIntent, AuthorIntentTextPolicy.normalize(intent));
    }

    private String requireIntent(String value) {
        if (!AuthorIntentTextPolicy.isValid(value)) {
            throw new IllegalArgumentException("작성자 의도 형식이 유효하지 않습니다.");
        }
        return AuthorIntentTextPolicy.normalize(value);
    }
}
