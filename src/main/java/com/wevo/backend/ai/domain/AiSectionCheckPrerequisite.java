package com.wevo.backend.ai.domain;

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
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사전 검토에 사용한 직접 상위 section version snapshot. */
@Entity
@Table(name = "ai_section_check_prerequisites")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiSectionCheckPrerequisite extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ai_section_check_id", nullable = false)
    private AiSectionCheck sectionCheck;

    @Column(name = "source_section_id", nullable = false)
    private Long sourceSectionId;

    @Column(name = "content_version", nullable = false)
    private Integer contentVersion;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Builder
    private AiSectionCheckPrerequisite(
            AiSectionCheck sectionCheck,
            Long sourceSectionId,
            Integer contentVersion,
            Integer sortOrder
    ) {
        this.sectionCheck = Objects.requireNonNull(sectionCheck, "sectionCheck는 필수입니다.");
        if (sourceSectionId == null || sourceSectionId <= 0
                || contentVersion == null || contentVersion <= 0
                || sortOrder == null || sortOrder <= 0) {
            throw new IllegalArgumentException("상위 section snapshot 값은 모두 1 이상이어야 합니다.");
        }
        this.sourceSectionId = sourceSectionId;
        this.contentVersion = contentVersion;
        this.sortOrder = sortOrder;
    }
}
