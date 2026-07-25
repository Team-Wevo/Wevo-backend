package com.wevo.backend.section.repository;

import com.wevo.backend.section.domain.SectionDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SectionDraftRepository extends JpaRepository<SectionDraft, Long> {

    boolean existsByProjectSection_Id(Long projectSectionId);

    /**
     * 섹션의 최신 버전 초안을 조회한다. (외부 검토자가 읽을 현재 본문)
     */
    Optional<SectionDraft> findTopByProjectSection_IdOrderByVersionDesc(Long sectionId);

    Optional<SectionDraft> findByProjectSection_IdAndVersion(Long sectionId, Integer version);

    /**
     * 프로젝트의 <b>확정된</b> 섹션에 대해 확정본(각 섹션의 {@code confirmedVersion} 에 해당하는
     * 초안)을 섹션 순서대로 조회한다. (최종 결과물 조립 — API_SPEC §3.6.1)
     *
     * <p>버전 조건을 섹션의 {@code confirmedVersion} 과 조인으로 맞춰 한 번에 가져온다.
     * 섹션마다 초안을 따로 조회하면 섹션 수만큼 쿼리가 나가므로(N+1) 단일 쿼리로 처리한다.
     * 응답 조립에 섹션의 제목·순서가 필요하므로 {@code JOIN FETCH} 로 함께 로딩한다
     * — {@code ProjectSection} 이 지연 로딩이라 fetch 없이는 섹션마다 추가 쿼리가 나간다.
     *
     * <p>버전만이 아니라 <b>{@code status = CONFIRMED} 도 함께</b> 건다. 확정 해제(reopen) 후
     * {@code confirmedVersion} 이 남아 있는 섹션의 지난 본문이 완성본에 섞여 들어가는 것을
     * 막기 위함이다 — 호출측의 개수 대조는 확정 섹션 수가 우연히 맞아떨어지면 이를 잡지 못한다.
     *
     * <p>{@code COALESCE} 는 엔티티의 {@code getConfirmedVersion()} 과 같은 규칙(레거시 행의
     * null 을 미확정 0 으로 간주)을 쿼리에서도 유지한다. 초안 버전은 1 부터라 0 은 매칭되지 않는다.
     */
    @Query("SELECT d FROM SectionDraft d JOIN FETCH d.projectSection s "
            + "WHERE s.project.id = :projectId "
            + "AND s.status = com.wevo.backend.section.domain.ProjectSectionStatus.CONFIRMED "
            + "AND d.version = COALESCE(s.confirmedVersion, 0) "
            + "ORDER BY s.sectionOrder")
    List<SectionDraft> findConfirmedDraftsByProjectId(@Param("projectId") Long projectId);
}
