package com.wevo.backend.project.domain;

import com.wevo.backend.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
    private Project(String title, String description, String ideaText,
                    OutputType resultType, String audience, ProjectStatus status) {
        this.title = title;
        this.description = description;
        this.ideaText = ideaText;
        this.resultType = resultType;
        this.audience = audience;
        this.status = status;
    }
}
