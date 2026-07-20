package com.wevo.backend.section.service;

import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import java.util.List;

/**
 * 프로젝트의 섹션 확정 진행도. (section 도메인이 타 도메인에 공개하는 조회 결과)
 *
 * <p>"전체 섹션 수 / 확정된 섹션 수"라는 개념의 <b>단일 정의</b>다. 프로젝트 상세의
 * {@code sectionProgress} 와 최종 결과물의 진행도는 모두 이 타입에서 파생하며, 확정 여부를
 * 세는 규칙({@link ProjectSectionStatus#CONFIRMED})도 여기에만 둔다.
 *
 * @param totalCount     전체 섹션 수
 * @param confirmedCount 확정({@code CONFIRMED}) 상태인 섹션 수
 */
public record SectionConfirmationSummary(int totalCount, int confirmedCount) {

    /**
     * 이미 로딩된 섹션 목록에서 진행도를 센다.
     *
     * <p>섹션 목록을 어차피 조회하는 경로(프로젝트 상세 등)가 집계 쿼리를 따로 날리지 않도록
     * 제공한다 — 세는 규칙은 집계 쿼리 경로와 동일하다.
     */
    public static SectionConfirmationSummary from(List<ProjectSection> sections) {
        int confirmedCount = (int) sections.stream()
                .filter(section -> section.getStatus() == ProjectSectionStatus.CONFIRMED)
                .count();

        return new SectionConfirmationSummary(sections.size(), confirmedCount);
    }

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
