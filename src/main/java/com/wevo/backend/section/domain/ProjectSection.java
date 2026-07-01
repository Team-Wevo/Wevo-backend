package com.wevo.backend.section.domain;

import com.wevo.backend.global.common.BaseTimeEntity;
import com.wevo.backend.project.domain.Project;
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
 * 프로젝트(보드)에 속한 개별 섹션. 의견 수집·초안·검토·확정의 단위.
 */
@Entity
@Table(name = "project_sections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectSection extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private SectionTemplate template;

    @Column(length = 200)
    private String title;

    @Column(name = "section_order")
    private Integer sectionOrder;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ProjectSectionStatus status;

    @Column(name = "confirmed_version")
    private Integer confirmedVersion;

    @Column(name = "needs_re_review")
    private Boolean needsReReview;

    @Builder
    private ProjectSection(Project project, SectionTemplate template, String title, Integer sectionOrder,
                           ProjectSectionStatus status, Integer confirmedVersion, Boolean needsReReview) {
        this.project = project;
        this.template = template;
        this.title = title;
        this.sectionOrder = sectionOrder;
        this.status = status;
        this.confirmedVersion = confirmedVersion;
        this.needsReReview = needsReReview;
    }
}
