package com.wevo.backend.section.domain;

import com.wevo.backend.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결과물 유형(result_type)별 섹션 구성 템플릿. 프로젝트 섹션의 원형이 된다.
 */
@Entity
@Table(name = "section_templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SectionTemplate extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "result_type", length = 50)
    private String resultType;

    @Column(name = "section_key", length = 100)
    private String sectionKey;

    @Column(length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "guide_text", columnDefinition = "TEXT")
    private String guideText;

    @Column(name = "order_no")
    private Integer orderNo;

    @Column(name = "is_required")
    private Boolean isRequired;

    @Builder
    private SectionTemplate(String resultType, String sectionKey, String title, String description,
                            String guideText, Integer orderNo, Boolean isRequired) {
        this.resultType = resultType;
        this.sectionKey = sectionKey;
        this.title = title;
        this.description = description;
        this.guideText = guideText;
        this.orderNo = orderNo;
        this.isRequired = isRequired;
    }
}
