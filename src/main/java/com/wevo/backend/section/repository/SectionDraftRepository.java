package com.wevo.backend.section.repository;

import com.wevo.backend.section.domain.SectionDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SectionDraftRepository extends JpaRepository<SectionDraft, Long> {

    /**
     * 섹션의 최신 버전 초안을 조회한다. (외부 검토자가 읽을 현재 본문)
     */
    Optional<SectionDraft> findTopByProjectSection_IdOrderByVersionDesc(Long sectionId);

    /**
     * 프로젝트의 모든 섹션에 대해 <b>확정본</b>(각 섹션의 {@code confirmedVersion} 에 해당하는
     * 초안)을 섹션 순서대로 조회한다. (최종 결과물 조립 — API_SPEC §3.6.1)
     *
     * <p>버전 조건을 섹션의 {@code confirmedVersion} 과 조인으로 맞춰 한 번에 가져온다.
     * 섹션마다 초안을 따로 조회하면 섹션 수만큼 쿼리가 나가므로(N+1) 단일 쿼리로 처리한다.
     * 응답 조립에 섹션의 제목·순서가 필요하므로 {@code JOIN FETCH} 로 함께 로딩한다
     * — {@code ProjectSection} 이 지연 로딩이라 fetch 없이는 섹션마다 추가 쿼리가 나간다.
     */
    @Query("SELECT d FROM SectionDraft d JOIN FETCH d.projectSection s "
            + "WHERE s.project.id = :projectId AND d.version = s.confirmedVersion "
            + "ORDER BY s.sectionOrder")
    List<SectionDraft> findConfirmedDraftsByProjectId(@Param("projectId") Long projectId);
}
