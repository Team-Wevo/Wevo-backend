package com.wevo.backend.ai.repository;

import com.wevo.backend.ai.domain.ProjectFlowCheckFinding;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectFlowCheckFindingRepository extends JpaRepository<ProjectFlowCheckFinding, Long> {
    List<ProjectFlowCheckFinding> findAllByFlowCheck_IdOrderBySortOrder(Long flowCheckId);
}
