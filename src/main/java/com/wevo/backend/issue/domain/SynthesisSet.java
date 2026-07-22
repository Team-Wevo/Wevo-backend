package com.wevo.backend.issue.domain;

import com.wevo.backend.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 성공한 의견 정리 실행의 불변 결과 세트.
 *
 * <p>외부 세트 식별자는 AI 작업의 {@code requestId}이며, 과거 세트는 삭제하거나 상태를 바꾸지 않는다.
 * 현재 세트는 AI 작업 이력에서 가장 최근에 성공한 실행으로 파생한다.
 */
@Entity
@Table(name = "synthesis_sets", uniqueConstraints = @UniqueConstraint(
        name = "uk_synthesis_sets_request", columnNames = "request_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SynthesisSet extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Column(name = "project_section_id", nullable = false, updatable = false)
    private Long projectSectionId;

    @Column(name = "opinion_gate_generation", nullable = false, updatable = false)
    private long opinionGateGeneration;

    @Column(name = "consensus_summary", columnDefinition = "TEXT", nullable = false, updatable = false)
    private String consensusSummary;

    @Builder
    private SynthesisSet(UUID requestId, Long projectSectionId, long opinionGateGeneration,
                         String consensusSummary) {
        this.requestId = Objects.requireNonNull(requestId, "requestId는 필수입니다.");
        this.projectSectionId = Objects.requireNonNull(projectSectionId, "projectSectionId는 필수입니다.");
        if (opinionGateGeneration < 0) {
            throw new IllegalArgumentException("opinionGateGeneration은 0 이상이어야 합니다.");
        }
        if (consensusSummary == null || consensusSummary.isBlank()) {
            throw new IllegalArgumentException("consensusSummary는 필수입니다.");
        }
        this.opinionGateGeneration = opinionGateGeneration;
        this.consensusSummary = consensusSummary;
    }
}
