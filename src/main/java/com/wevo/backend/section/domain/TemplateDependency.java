package com.wevo.backend.section.domain;

import com.wevo.backend.global.common.BaseTimeEntity;
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
 * 템플릿 간 의존 관계 (예: A 섹션이 확정되어야 B 섹션을 작성 가능 등).
 */
@Entity
@Table(name = "template_dependencies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TemplateDependency extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_template_id")
    private SectionTemplate fromTemplate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_template_id")
    private SectionTemplate toTemplate;

    @Enumerated(EnumType.STRING)
    @Column(name = "dependency_type", length = 30)
    private TemplateDependencyType dependencyType;

    @Builder
    private TemplateDependency(SectionTemplate fromTemplate, SectionTemplate toTemplate,
                               TemplateDependencyType dependencyType) {
        this.fromTemplate = fromTemplate;
        this.toTemplate = toTemplate;
        this.dependencyType = dependencyType;
    }
}
