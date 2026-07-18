package com.wevo.backend.section.domain;

/**
 * AI 사전 검토가 현재 본문 기준 최신인지 나타내는 overlay 플래그. (정책서 §5.3.3, CLAUDE.md §5.7)
 *
 * <p>섹션 확정의 필수 조건이다(§6.3 — {@code CURRENT}여야 확정 가능).
 * <b>성공한 사전 검토가 없으면 값 자체가 없다(null)</b> — 확정 조건 판정에서 null은 미충족으로
 * 다룬다. (API_SPEC §3.8.4-2)
 *
 * <p>{@code OUTDATED} 트리거는 <b>본문 직접 수정뿐</b>이다 — 상위 섹션 변경은 이 플래그가 아니라
 * {@link DriftStatus}가 담당한다. (API_SPEC §3.7.2 층위 구분)
 */
public enum AiCheckStatus {

    /** 사전 검토가 현재 본문 기준 최신. */
    CURRENT,

    /** 본문이 수정되어 사전 검토가 낡음 — 재실행 필요. */
    OUTDATED
}
