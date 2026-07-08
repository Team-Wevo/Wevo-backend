package com.wevo.backend.section.domain;

/**
 * 섹션의 내부 진행 단계(단일 값). (제품 정책서 §8 sectionStatus)
 *
 * <p>화면의 4단계(시작 전/작성 중/작성 완료/문제 있음)는 저장하지 않고 이 값 + 오버레이 플래그
 * (driftStatus 등)에서 파생한다. 그래서 "시작 전"에 해당하는 NOT_STARTED는 상태값으로 두지 않는다.
 */
public enum ProjectSectionStatus {
    COLLECTING,     // 의견 수집
    SYNTHESIZING,   // AI 쟁점 조율·정리 (Wevo 차별점)
    DRAFTING,       // 초안 작성
    REVIEWING,      // 검토
    CONFIRMED       // 확정
}
