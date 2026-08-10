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
     * 섹션의 초안 버전 이력을 <b>최신 버전부터</b> 조회한다. (이력 목록 — API_SPEC §3.7.8)
     *
     * <p>편집자 표시 이름을 함께 내리므로 {@code lastEditor} 를 {@code JOIN FETCH} 한다 —
     * 지연 로딩으로 두면 버전 수만큼 사용자 조회가 나간다(N+1). 편집자가 없는 행
     * (AI 생성본·레거시 데이터)도 목록에서 빠지면 안 되므로 {@code LEFT} 조인이다.
     *
     * <p>본문({@code content})은 목록에서 쓰지 않지만 엔티티를 통째로 읽는다 — 버전 수가 적은
     * MVP 규모에서 본문 없는 별도 투영을 두는 것보다 조회 경로를 하나로 유지하는 편이 낫다.
     * 이력이 길어져 전송량이 문제가 되면 그때 투영으로 바꾼다.
     */
    @Query("SELECT d FROM SectionDraft d LEFT JOIN FETCH d.lastEditor "
            + "WHERE d.projectSection.id = :sectionId ORDER BY d.version DESC")
    List<SectionDraft> findVersionHistoryBySectionId(@Param("sectionId") Long sectionId);

    /**
     * 섹션의 특정 버전 초안을 편집자와 함께 조회한다. (버전 본문 조회 — API_SPEC §3.7.9)
     *
     * <p>{@link #findByProjectSection_IdAndVersion} 과 대상은 같고 편집자를 함께 로딩하는 점만
     * 다르다 — 응답에 편집자 이름이 들어가므로 조회를 두 번 하지 않는다.
     */
    @Query("SELECT d FROM SectionDraft d LEFT JOIN FETCH d.lastEditor "
            + "WHERE d.projectSection.id = :sectionId AND d.version = :version")
    Optional<SectionDraft> findVersionWithEditor(@Param("sectionId") Long sectionId,
                                                 @Param("version") Integer version);

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
    @Query("SELECT d FROM SectionDraft d JOIN FETCH d.projectSection s LEFT JOIN FETCH s.template t "
            + "WHERE s.project.id = :projectId "
            + "AND s.status = com.wevo.backend.section.domain.ProjectSectionStatus.CONFIRMED "
            + "AND d.version = COALESCE(s.confirmedVersion, 0) "
            + "ORDER BY s.sectionOrder")
    List<SectionDraft> findConfirmedDraftsByProjectId(@Param("projectId") Long projectId);
}
