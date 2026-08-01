package com.wevo.backend.ai.repository;

import com.wevo.backend.ai.domain.ProjectFlowFindingSection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectFlowFindingSectionRepository extends JpaRepository<ProjectFlowFindingSection, Long> {
    @Query("select reference from ProjectFlowFindingSection reference "
            + "join fetch reference.finding finding "
            + "where finding.flowCheck.id = :checkId "
            + "order by finding.sortOrder, reference.sortOrder")
    List<ProjectFlowFindingSection> findAllForCheck(@Param("checkId") Long checkId);
}
