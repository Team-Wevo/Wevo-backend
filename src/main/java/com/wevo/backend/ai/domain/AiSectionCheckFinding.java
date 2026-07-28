package com.wevo.backend.ai.domain;

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
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사전 검토 결과의 정렬된 개별 finding. */
@Entity
@Table(name = "ai_section_check_findings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiSectionCheckFinding extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ai_section_check_id", nullable = false)
    private AiSectionCheck sectionCheck;

    @Enumerated(EnumType.STRING)
    @Column(name = "finding_type", nullable = false, length = 40)
    private AiSectionFindingType type;

    @Column(name = "target_excerpt", nullable = false)
    private String targetExcerpt;

    @Column(name = "comment_text", nullable = false)
    private String comment;

    @Column(name = "suggestion", nullable = false)
    private String suggestion;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Builder
    private AiSectionCheckFinding(
            AiSectionCheck sectionCheck,
            AiSectionFindingType type,
            String targetExcerpt,
            String comment,
            String suggestion,
            Integer sortOrder
    ) {
        this.sectionCheck = Objects.requireNonNull(sectionCheck, "sectionCheck는 필수입니다.");
        this.type = Objects.requireNonNull(type, "type은 필수입니다.");
        this.targetExcerpt = requireText(targetExcerpt, "targetExcerpt");
        this.comment = requireText(comment, "comment");
        this.suggestion = requireText(suggestion, "suggestion");
        if (sortOrder == null || sortOrder <= 0) {
            throw new IllegalArgumentException("sortOrder는 1 이상이어야 합니다.");
        }
        this.sortOrder = sortOrder;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "는 필수입니다.");
        }
        return value;
    }
}
