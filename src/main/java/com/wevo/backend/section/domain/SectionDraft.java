package com.wevo.backend.section.domain;

import com.wevo.backend.global.common.BaseTimeEntity;
import com.wevo.backend.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 섹션의 버전별 초안 본문. (AI 통합 초안 / 편집 이력)
 */
@Entity
@Table(name = "section_drafts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SectionDraft extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_section_id")
    private ProjectSection projectSection;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column
    private Integer version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_editor_user_id")
    private User lastEditor;

    @Builder
    private SectionDraft(ProjectSection projectSection, String content, Integer version, User lastEditor) {
        this.projectSection = projectSection;
        this.content = content;
        this.version = version;
        this.lastEditor = lastEditor;
    }
}
