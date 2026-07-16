package com.wevo.backend.project.domain;

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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "projects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project extends BaseTimeEntity {

    /** 프로젝트 최대 인원 — 팀장 1 + 팀원 3. (제품 정책서 §2.1) */
    public static final int MAX_MEMBERS = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id")
    private User owner;

    @Column(length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "idea_text", columnDefinition = "TEXT")
    private String ideaText;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_type", length = 30)
    private OutputType resultType;

    @Column(length = 200)
    private String audience;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ProjectStatus status;

    @Builder
    private Project(User owner, String title, String description, String ideaText,
                    OutputType resultType, String audience, ProjectStatus status) {
        this.owner = owner;
        this.title = title;
        this.description = description;
        this.ideaText = ideaText;
        this.resultType = resultType;
        this.audience = audience;
        this.status = status;
    }
}
