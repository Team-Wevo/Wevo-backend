package com.wevo.backend.ai.repository;

import com.wevo.backend.ai.domain.AiSectionCheckFinding;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiSectionCheckFindingRepository
        extends JpaRepository<AiSectionCheckFinding, Long> {

    List<AiSectionCheckFinding> findBySectionCheck_IdOrderBySortOrderAsc(Long sectionCheckId);
}
