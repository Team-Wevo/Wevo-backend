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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 템플릿 간 직접 의존 관계.
 *
 * <p>제품 정책의 {@code dependsOn}은 {@code REQUIRES}로 저장하며 방향은
 * {@code fromTemplate = 의존하는 하위}, {@code toTemplate = 의존 대상 상위}다.
 * 화면 순서나 간접 선행 관계는 이 그래프에 포함하지 않는다.
 */
@Entity
@Table(
        name = "template_dependencies",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_template_dependencies_pair_type",
                columnNames = {"from_template_id", "to_template_id", "dependency_type"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TemplateDependency extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_template_id", nullable = false)
    private SectionTemplate fromTemplate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_template_id", nullable = false)
    private SectionTemplate toTemplate;

    @Enumerated(EnumType.STRING)
    @Column(name = "dependency_type", length = 30, nullable = false)
    private TemplateDependencyType dependencyType;

    @Builder
    private TemplateDependency(SectionTemplate fromTemplate, SectionTemplate toTemplate,
                               TemplateDependencyType dependencyType) {
        if (fromTemplate == null || toTemplate == null) {
            throw new IllegalArgumentException("의존 관계의 양쪽 템플릿은 필수입니다.");
        }
        if (fromTemplate == toTemplate) {
            throw new IllegalArgumentException("템플릿은 자기 자신에 의존할 수 없습니다.");
        }
        if (fromTemplate.getResultType() != toTemplate.getResultType()) {
            throw new IllegalArgumentException("서로 다른 결과물 유형의 템플릿을 연결할 수 없습니다.");
        }
        if (dependencyType == null) {
            throw new IllegalArgumentException("의존 관계 type은 필수입니다.");
        }
        this.fromTemplate = fromTemplate;
        this.toTemplate = toTemplate;
        this.dependencyType = dependencyType;
    }
}
