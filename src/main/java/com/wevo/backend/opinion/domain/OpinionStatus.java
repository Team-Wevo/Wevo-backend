package com.wevo.backend.opinion.domain;

/**
 * 의견의 임시저장/제출 상태. (제품 정책서 §5)
 *
 * <ul>
 *   <li>{@link #DRAFT} — 임시저장. 본인만 볼 수 있다.</li>
 *   <li>{@link #SUBMITTED} — 제출됨. 팀에 공개되며, 본인이 제출해야 타인 의견을 열람할 수 있다(공개 게이트).</li>
 * </ul>
 */
public enum OpinionStatus {
    DRAFT,
    SUBMITTED
}
