package com.wevo.backend.opinion.domain;

/**
 * 의견 수집 현황에서 <b>멤버 한 명의 진행 상태</b>. (정책서 §4.5 — 수집 마감 전 미제출 파악)
 *
 * <p><b>저장하지 않는 파생 값이다.</b> DB 에 남는 의견 상태는 {@link OpinionStatus}(`DRAFT`/`SUBMITTED`)
 * 뿐이며, 이 enum 은 "멤버 로스터 × 의견 유무"를 조합해 조회 시점에 계산한다.
 * 팀 검토의 {@code PENDING} 을 파생 노출하는 방식(§6.1)과 같은 취급이다.
 *
 * <p>{@link OpinionStatus} 에 상태를 더하지 않고 별도 enum 을 둔 이유는, 의견 행이 아직 없는
 * 사람({@link #NOT_STARTED})은 {@code OpinionStatus} 로 표현할 대상 자체가 없기 때문이다.
 * {@code OpinionStatus} 는 DB 제약이 걸린 저장 값이라 "행이 없음"을 값으로 넣을 수 없다.
 */
public enum OpinionCollectionState {

    /** 의견을 아직 만들지 않음 (임시저장 이력조차 없음). */
    NOT_STARTED,

    /** 임시저장만 있고 제출하지 않음 — 정책서 §4.5 의 "작성 중". */
    DRAFTING,

    /**
     * 제출을 마침.
     *
     * <p>제출 후 재편집 중이어도 제출본은 팀에 남아 있으므로(§4.1) 제출로 센다.
     * 재편집 여부는 별도 플래그로 구분한다.
     */
    SUBMITTED
}
