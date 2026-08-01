package com.wevo.backend.ai.repository;

import com.wevo.backend.ai.domain.ProjectFlowCheckInput;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectFlowCheckInputRepository extends JpaRepository<ProjectFlowCheckInput, Long> {
    List<ProjectFlowCheckInput> findAllByFlowCheck_IdOrderBySortOrder(Long flowCheckId);
}
