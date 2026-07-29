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
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 초안 생성 시 사용한 직접 상위 확정본 version 참조. */
@Entity
@Table(name = "section_draft_evidence_prerequisites", uniqueConstraints = {
        @UniqueConstraint(name = "uk_draft_evidence_prerequisite",
                columnNames = {"draft_evidence_id", "source_section_id"}),
        @UniqueConstraint(name = "uk_draft_evidence_prerequisite_order",
                columnNames = {"draft_evidence_id", "sort_order"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SectionDraftEvidencePrerequisite extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "draft_evidence_id", nullable = false, updatable = false)
    private SectionDraftEvidence draftEvidence;

    @Column(name = "source_section_id", nullable = false, updatable = false)
    private Long sourceSectionId;

    @Column(name = "content_version", nullable = false, updatable = false)
    private int contentVersion;

    @Column(name = "sort_order", nullable = false, updatable = false)
    private int sortOrder;

    @Builder
    private SectionDraftEvidencePrerequisite(
            SectionDraftEvidence draftEvidence,
            Long sourceSectionId,
            int contentVersion,
            int sortOrder
    ) {
        this.draftEvidence = Objects.requireNonNull(draftEvidence, "draftEvidence는 필수입니다.");
        this.sourceSectionId = Objects.requireNonNull(sourceSectionId, "sourceSectionId는 필수입니다.");
        if (contentVersion <= 0 || sortOrder <= 0) {
            throw new IllegalArgumentException("contentVersion과 sortOrder는 1 이상이어야 합니다.");
        }
        this.contentVersion = contentVersion;
        this.sortOrder = sortOrder;
    }
}
