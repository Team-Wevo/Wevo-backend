package com.wevo.backend.section.domain;

/**
 * 상위 섹션 변경으로 재검토가 필요한지 나타내는 overlay 플래그. (정책서 §6.4, CLAUDE.md §5.7)
 *
 * <p>{@code sectionStatus}와 독립이며, 확정(confirm) 성공 시 {@link #NONE}으로 복귀한다.
 * (API_SPEC §3.7.2 드리프트 규칙·§3.7.6)
 */
public enum DriftStatus {

    /** 상위 변경 영향 없음 (기본값). */
    NONE,

    /** 직접 의존하는 상위 섹션이 수정되어 재검토 필요. */
    REVIEW_REQUIRED
}
