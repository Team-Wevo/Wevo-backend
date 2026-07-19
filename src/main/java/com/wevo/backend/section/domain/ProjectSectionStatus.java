package com.wevo.backend.section.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 섹션의 내부 진행 단계(단일 값). (제품 정책서 §8 sectionStatus)
 *
 * <p>화면의 4단계(시작 전/작성 중/작성 완료/문제 있음)는 저장하지 않고 이 값 + 오버레이 플래그
 * (driftStatus 등)에서 파생한다. 그래서 "시작 전"에 해당하는 NOT_STARTED는 상태값으로 두지 않는다.
 *
 * <p>상태는 <b>정해진 액션으로만</b> 전이한다(직접 변경 금지, §3.2). 허용 전이는 {@link #ALLOWED_TRANSITIONS}
 * 에 한 곳으로 모아 관리하며, 전이 실행/권한/이력은 section 도메인이 소유한다. (스프린트 안건 ⑥)
 */
public enum ProjectSectionStatus {
    COLLECTING,     // 의견 수집
    SYNTHESIZING,   // AI 쟁점 조율·정리 (Wevo 차별점)
    DRAFTING,       // 초안 작성
    REVIEWING,      // 검토
    CONFIRMED;      // 확정

    /**
     * 허용된 상태 전이. 수집 재오픈은 미확정 단계에서 {@code COLLECTING} 으로 되돌릴 수 있다.
     *
     * <ul>
     *   <li>COLLECTING → SYNTHESIZING : 수집 마감</li>
     *   <li>SYNTHESIZING → DRAFTING   : 쟁점 결정 반영</li>
     *   <li>DRAFTING → REVIEWING      : 검토 요청</li>
     *   <li>REVIEWING → CONFIRMED     : 확정</li>
     * </ul>
     */
    private static final Map<ProjectSectionStatus, Set<ProjectSectionStatus>> ALLOWED_TRANSITIONS =
            new EnumMap<>(ProjectSectionStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(COLLECTING, EnumSet.of(SYNTHESIZING));
        ALLOWED_TRANSITIONS.put(SYNTHESIZING, EnumSet.of(COLLECTING, DRAFTING));
        ALLOWED_TRANSITIONS.put(DRAFTING, EnumSet.of(COLLECTING, REVIEWING));
        ALLOWED_TRANSITIONS.put(REVIEWING, EnumSet.of(COLLECTING, CONFIRMED));
        ALLOWED_TRANSITIONS.put(CONFIRMED, EnumSet.noneOf(ProjectSectionStatus.class));
    }

    /**
     * 현재 상태에서 {@code target} 으로 전이가 허용되는지 반환한다.
     */
    public boolean canTransitionTo(ProjectSectionStatus target) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    /**
     * 본문(초안) 저장을 허용하는 단계인지 반환한다.
     *
     * <ul>
     *   <li>{@code DRAFTING} — 초안 작성 단계</li>
     *   <li>{@code REVIEWING} — 검토 중 수정 반영(기존 검토는 만료 처리된다, §6.1)</li>
     * </ul>
     *
     * <p>수집/정리 단계({@code COLLECTING}·{@code SYNTHESIZING})는 아직 초안 단계가 아니고,
     * 확정({@code CONFIRMED})은 잠긴 상태라 저장을 허용하지 않는다. (확정 후 변경은 드리프트 §6.4)
     */
    public boolean allowsDraftEditing() {
        return this == DRAFTING || this == REVIEWING;
    }
}
