package com.wevo.backend.section.service;

/**
 * 프로젝트의 섹션 확정 진행도. (section 도메인이 타 도메인에 공개하는 조회 결과)
 *
 * @param totalCount     전체 섹션 수
 * @param confirmedCount 확정({@code CONFIRMED}) 상태인 섹션 수
 */
public record SectionConfirmationSummary(int totalCount, int confirmedCount) {

    /**
     * 모든 섹션이 확정됐는지 여부.
     *
     * <p>섹션이 하나도 없는 프로젝트는 "전부 확정"으로 보지 않는다 — 빈 프로젝트가
     * 완성본으로 취급되면 안 된다. (정책서 §2.3)
     */
    public boolean allConfirmed() {
        return totalCount > 0 && confirmedCount == totalCount;
    }
}
