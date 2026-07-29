package com.wevo.backend.issue.domain;

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

/** 합의점이 직접 근거로 삼은 제출 의견의 생성 시점 스냅샷. */
@Entity
@Table(
        name = "synthesis_consensus_evidence",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_synthesis_consensus_evidence_set_opinion",
                        columnNames = {"synthesis_set_id", "opinion_id"}
                ),
                @UniqueConstraint(
                        name = "uk_synthesis_consensus_evidence_set_order",
                        columnNames = {"synthesis_set_id", "sort_order"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SynthesisConsensusEvidence extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "synthesis_set_id", nullable = false, updatable = false)
    private SynthesisSet synthesisSet;

    @Column(name = "opinion_id", nullable = false, updatable = false)
    private Long opinionId;

    @Column(name = "author_user_id", nullable = false, updatable = false)
    private Long authorUserId;

    @Column(name = "author_name_snapshot", nullable = false, updatable = false, length = 100)
    private String authorNameSnapshot;

    @Column(name = "excerpt", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String excerpt;

    @Column(name = "sort_order", nullable = false, updatable = false)
    private int sortOrder;

    @Builder
    private SynthesisConsensusEvidence(
            SynthesisSet synthesisSet,
            Long opinionId,
            Long authorUserId,
            String authorNameSnapshot,
            String excerpt,
            int sortOrder
    ) {
        this.synthesisSet = Objects.requireNonNull(synthesisSet, "synthesisSet은 필수입니다.");
        this.opinionId = Objects.requireNonNull(opinionId, "opinionId는 필수입니다.");
        this.authorUserId = Objects.requireNonNull(authorUserId, "authorUserId는 필수입니다.");
        this.authorNameSnapshot = requireText(authorNameSnapshot, "authorNameSnapshot");
        this.excerpt = requireText(excerpt, "excerpt");
        if (sortOrder <= 0) {
            throw new IllegalArgumentException("sortOrder는 1 이상이어야 합니다.");
        }
        this.sortOrder = sortOrder;
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "는 필수입니다.");
        }
        return value;
    }
}
