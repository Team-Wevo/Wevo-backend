package com.wevo.backend.section.domain;

import com.wevo.backend.global.common.BaseTimeEntity;
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
 * 섹션 내 의견을 분류하는 라벨(항목). 의견 블록이 이 라벨에 묶인다.
 */
@Entity
@Table(name = "section_labels")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SectionLabel extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_section_id")
    private ProjectSection projectSection;

    @Column(length = 100)
    private String name;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Builder
    private SectionLabel(ProjectSection projectSection, String name, Integer sortOrder) {
        this.projectSection = projectSection;
        this.name = name;
        this.sortOrder = sortOrder;
    }
}
