package com.wevo.backend.opinion.domain;

import com.wevo.backend.global.common.BaseTimeEntity;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.SectionLabel;
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
 * 팀원이 섹션(항목)에 작성한 개별 의견. AI가 이들을 모아 섹션 초안으로 통합한다.
 */
@Entity
@Table(name = "opinion_blocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OpinionBlock extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_section_id")
    private ProjectSection projectSection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_label_id")
    private SectionLabel sectionLabel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_user_id")
    private User author;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column
    private Integer version;

    @Column(name = "is_deleted")
    private Boolean isDeleted;

    @Builder
    private OpinionBlock(ProjectSection projectSection, SectionLabel sectionLabel, User author,
                         String content, Integer version, Boolean isDeleted) {
        this.projectSection = projectSection;
        this.sectionLabel = sectionLabel;
        this.author = author;
        this.content = content;
        this.version = version;
        this.isDeleted = isDeleted;
    }
}
