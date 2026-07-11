package com.wevo.backend.section.repository;

import com.wevo.backend.section.domain.ProjectSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectSectionRepository extends JpaRepository<ProjectSection, Long> {

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
