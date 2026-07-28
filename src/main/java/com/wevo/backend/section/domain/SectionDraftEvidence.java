package com.wevo.backend.section.domain;

import com.wevo.backend.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** AI 초안 버전에 고정된 synthesis 및 생성 입력 스냅샷. */
@Entity
@Table(name = "section_draft_evidence", uniqueConstraints = {
        @UniqueConstraint(name = "uk_section_draft_evidence_draft", columnNames = "section_draft_id"),
        @UniqueConstraint(name = "uk_section_draft_evidence_request", columnNames = "generation_request_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SectionDraftEvidence extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_draft_id", nullable = false, updatable = false)
    private SectionDraft sectionDraft;

    @Column(name = "synthesis_set_id", nullable = false, updatable = false)
    private Long synthesisSetId;

    @Column(name = "opinion_gate_generation", nullable = false, updatable = false)
    private long opinionGateGeneration;

    @Column(name = "generation_request_id", nullable = false, updatable = false)
    private UUID generationRequestId;

    @Column(name = "input_snapshot_hash", nullable = false, length = 64, updatable = false)
    private String inputSnapshotHash;

    @Column(name = "source_version", nullable = false, length = 100, updatable = false)
    private String sourceVersion;

    @Column(name = "consensus_summary", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String consensusSummary;

    @Builder
    private SectionDraftEvidence(
            SectionDraft sectionDraft,
            Long synthesisSetId,
            long opinionGateGeneration,
            UUID generationRequestId,
            String inputSnapshotHash,
            String sourceVersion,
            String consensusSummary
    ) {
        this.sectionDraft = Objects.requireNonNull(sectionDraft, "sectionDraft는 필수입니다.");
        this.synthesisSetId = Objects.requireNonNull(synthesisSetId, "synthesisSetId는 필수입니다.");
        if (opinionGateGeneration < 0) {
            throw new IllegalArgumentException("opinionGateGeneration은 0 이상이어야 합니다.");
        }
        this.opinionGateGeneration = opinionGateGeneration;
        this.generationRequestId = Objects.requireNonNull(generationRequestId, "generationRequestId는 필수입니다.");
        this.inputSnapshotHash = requireText(inputSnapshotHash, "inputSnapshotHash");
        this.sourceVersion = requireText(sourceVersion, "sourceVersion");
        this.consensusSummary = requireText(consensusSummary, "consensusSummary");
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "는 필수입니다.");
        }
        return value;
    }
}
