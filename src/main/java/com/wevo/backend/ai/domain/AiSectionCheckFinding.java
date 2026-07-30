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

    public static final int MAX_TARGET_EXCERPT_LENGTH = 1_000;
    public static final int MAX_COMMENT_LENGTH = 1_000;
    public static final int MAX_SUGGESTION_LENGTH = 1_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ai_section_check_id", nullable = false)
    private AiSectionCheck sectionCheck;

    @Enumerated(EnumType.STRING)
    @Column(name = "finding_type", nullable = false, length = 40)
    private AiSectionFindingType type;

    @Column(
            name = "target_excerpt",
            nullable = false,
            length = MAX_TARGET_EXCERPT_LENGTH
    )
    private String targetExcerpt;

    @Column(name = "comment_text", nullable = false, length = MAX_COMMENT_LENGTH)
    private String comment;

    @Column(name = "suggestion", nullable = false, length = MAX_SUGGESTION_LENGTH)
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
        this.targetExcerpt = requireText(
                targetExcerpt, "targetExcerpt", MAX_TARGET_EXCERPT_LENGTH);
        this.comment = requireText(comment, "comment", MAX_COMMENT_LENGTH);
        this.suggestion = requireText(suggestion, "suggestion", MAX_SUGGESTION_LENGTH);
        if (sortOrder == null || sortOrder <= 0) {
            throw new IllegalArgumentException("sortOrder는 1 이상이어야 합니다.");
        }
        this.sortOrder = sortOrder;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + "는 공백이 아니며 " + maxLength + "자 이하여야 합니다.");
        }
        return value;
    }
}
