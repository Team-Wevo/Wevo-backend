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

/** 새 정리 세트가 입력으로 사용한 이전 세트 GAP 답변의 원본 참조. */
@Entity
@Table(name = "synthesis_inherited_gap_answers", uniqueConstraints = @UniqueConstraint(
        name = "uk_synthesis_inherited_answers_set_answer",
        columnNames = {"synthesis_set_id", "source_answer_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SynthesisInheritedGapAnswer extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "synthesis_set_id", nullable = false, updatable = false)
    private SynthesisSet synthesisSet;

    @Column(name = "source_issue_id", nullable = false, updatable = false)
    private Long sourceIssueId;

    @Column(name = "source_answer_id", nullable = false, updatable = false)
    private Long sourceAnswerId;

    @Builder
    private SynthesisInheritedGapAnswer(SynthesisSet synthesisSet,
                                        Long sourceIssueId, Long sourceAnswerId) {
        this.synthesisSet = Objects.requireNonNull(synthesisSet, "synthesisSet은 필수입니다.");
        this.sourceIssueId = Objects.requireNonNull(sourceIssueId, "sourceIssueId는 필수입니다.");
        this.sourceAnswerId = Objects.requireNonNull(sourceAnswerId, "sourceAnswerId는 필수입니다.");
    }
}
