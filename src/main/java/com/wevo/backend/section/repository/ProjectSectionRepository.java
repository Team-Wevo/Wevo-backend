package com.wevo.backend.section.repository;

import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectSectionRepository extends JpaRepository<ProjectSection, Long> {

    /**
     * 섹션 행을 배타 잠금(PESSIMISTIC_WRITE)으로 조회한다.
     *
     * <p>섹션 상태 확인 후 쓰기가 이어지는 작업(의견 upsert, 수집 마감 등)을 직렬화해
     * 동시 요청이 유니크 제약 충돌이나 마감 직후 저장 같은 경합을 일으키지 않게 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ProjectSection s WHERE s.id = :id")
    Optional<ProjectSection> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ProjectSection s WHERE s.id IN :ids ORDER BY s.id")
    List<ProjectSection> findAllByIdInOrderByIdForUpdate(@Param("ids") List<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ProjectSection s WHERE s.project.id = :projectId ORDER BY s.id")
    List<ProjectSection> findAllByProjectIdForUpdate(@Param("projectId") Long projectId);

    List<ProjectSection> findByProjectIdOrderBySectionOrder(Long projectId);

    List<ProjectSection> findByProject_IdAndTemplate_IdInOrderBySectionOrderAscIdAsc(
            Long projectId, List<Long> templateIds);

    /**
     * 직접 하위 드리프트 전파 대상을 안정된 섹션 순서로 잠근다.
     *
     * <p>팀 검토 제출도 섹션 행 잠금을 사용하므로, 전파와 검토 무효화 사이의 경합을 막는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ProjectSection s JOIN FETCH s.template t "
            + "WHERE s.project.id = :projectId AND t.id IN :templateIds "
            + "ORDER BY s.sectionOrder ASC, s.id ASC")
    List<ProjectSection> findDependentsForUpdate(
            @Param("projectId") Long projectId,
            @Param("templateIds") List<Long> templateIds);

    long countByProjectId(Long projectId);

    long countByProjectIdAndStatus(Long projectId, ProjectSectionStatus status);

    /**
     * 프로젝트의 섹션을 순서대로 조회하되, 템플릿을 함께 로딩한다.
     *
     * <p>섹션 응답의 핵심 질문·가이드는 연결된 {@code SectionTemplate} 에서 읽으므로,
     * {@code LEFT JOIN FETCH} 로 미리 가져와 섹션 수만큼 추가 쿼리가 나가는 N+1 을 방지한다.
     */
    @Query("SELECT s FROM ProjectSection s LEFT JOIN FETCH s.template "
            + "WHERE s.project.id = :projectId ORDER BY s.sectionOrder")
    List<ProjectSection> findAllWithTemplateByProjectId(@Param("projectId") Long projectId);

    @Query("SELECT s FROM ProjectSection s LEFT JOIN FETCH s.template "
            + "WHERE s.id = :sectionId")
    Optional<ProjectSection> findByIdWithTemplate(@Param("sectionId") Long sectionId);
}
