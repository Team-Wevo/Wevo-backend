package com.wevo.backend.section.repository;

import com.wevo.backend.section.domain.ProjectSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectSectionRepository extends JpaRepository<ProjectSection, Long> {

    List<ProjectSection> findByProjectIdOrderBySectionOrder(Long projectId);
}
