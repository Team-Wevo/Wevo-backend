package com.wevo.backend.ai.repository;

import com.wevo.backend.ai.domain.ProjectFlowCheck;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectFlowCheckRepository extends JpaRepository<ProjectFlowCheck, Long> {
    Optional<ProjectFlowCheck> findBySourceJob_Id(Long sourceJobId);
}
