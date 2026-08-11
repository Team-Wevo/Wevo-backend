package com.wevo.backend.section.repository;

import com.wevo.backend.section.domain.SectionDraft;
import com.wevo.backend.section.service.SectionDraftVersionContent;
import com.wevo.backend.section.service.SectionDraftVersionSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
     * 섹션의 초안 버전 이력을 <b>최신 버전부터</b> 페이지 단위로 조회한다. (API_SPEC §3.7.8)
     *
     * <p><b>본문을 읽지 않는다</b> — 목록에 필요한 것은 길이뿐이라 {@code LENGTH} 로 DB 에서
     * 계산해 담는다. 엔티티를 그대로 읽으면 이력이 쌓일수록 화면에 쓰지도 않을 본문 전체가
     * 메모리로 올라온다. 길이 계산은 {@code content} 가 nullable 이라 {@code COALESCE} 로 0 을 채운다.
     *
     * <p>편집자는 {@code LEFT JOIN} 으로 <b>ID·이름만</b> 가져온다 — 조인하지 않으면 버전 수만큼
     * 사용자 조회가 나가고(N+1), 엔티티를 통째로 넘기면 section 의 응답 DTO 가 user 엔티티에
     * 의존하게 된다 (CLAUDE.md §6). 편집자가 없는 행(AI 생성본·레거시)도 목록에서 빠지면 안 되므로
     * {@code INNER} 가 아니라 {@code LEFT} 다.
     *
     * <p>전체 건수는 별도 count 쿼리로 센다 — 생성자 표현식 쿼리는 count 로 자동 변환되지 않는다.
     */
    @Query(value = "SELECT new com.wevo.backend.section.service.SectionDraftVersionSummary("
            + "d.version, COALESCE(LENGTH(d.content), 0), e.id, e.name, d.createdAt) "
            + "FROM SectionDraft d LEFT JOIN d.lastEditor e "
            + "WHERE d.projectSection.id = :sectionId ORDER BY d.version DESC",
            countQuery = "SELECT COUNT(d) FROM SectionDraft d WHERE d.projectSection.id = :sectionId")
    Page<SectionDraftVersionSummary> findVersionSummaries(@Param("sectionId") Long sectionId,
                                                          Pageable pageable);

    /**
     * 섹션의 특정 버전 본문을 편집자 정보와 함께 조회한다. (API_SPEC §3.7.9)
     *
     * <p>{@link #findByProjectSection_IdAndVersion} 과 대상은 같고, 엔티티 대신 응답에 필요한 값만
     * 담아 돌려준다는 점이 다르다 — 편집자 이름 때문에 조회를 두 번 하지 않으면서도
     * {@code User} 엔티티를 도메인 밖으로 내보내지 않는다. (CLAUDE.md §6)
     */
    @Query("SELECT new com.wevo.backend.section.service.SectionDraftVersionContent("
            + "d.version, d.content, e.id, e.name, d.createdAt) "
            + "FROM SectionDraft d LEFT JOIN d.lastEditor e "
            + "WHERE d.projectSection.id = :sectionId AND d.version = :version")
    Optional<SectionDraftVersionContent> findVersionContent(@Param("sectionId") Long sectionId,
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
