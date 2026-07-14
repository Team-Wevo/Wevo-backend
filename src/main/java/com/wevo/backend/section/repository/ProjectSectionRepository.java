package com.wevo.backend.section.repository;

import com.wevo.backend.section.domain.ProjectSection;
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

    List<ProjectSection> findByProjectIdOrderBySectionOrder(Long projectId);

    /**
     * 프로젝트의 섹션을 순서대로 조회하되, 템플릿을 함께 로딩한다.
     *
     * <p>섹션 응답의 핵심 질문·가이드는 연결된 {@code SectionTemplate} 에서 읽으므로,
     * {@code LEFT JOIN FETCH} 로 미리 가져와 섹션 수만큼 추가 쿼리가 나가는 N+1 을 방지한다.
     */
    @Query("SELECT s FROM ProjectSection s LEFT JOIN FETCH s.template "
            + "WHERE s.project.id = :projectId ORDER BY s.sectionOrder")
    List<ProjectSection> findAllWithTemplateByProjectId(@Param("projectId") Long projectId);
}
