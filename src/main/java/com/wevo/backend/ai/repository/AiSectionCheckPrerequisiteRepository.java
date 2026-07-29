package com.wevo.backend.ai.repository;

import com.wevo.backend.ai.domain.AiSectionCheckPrerequisite;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiSectionCheckPrerequisiteRepository
        extends JpaRepository<AiSectionCheckPrerequisite, Long> {

    List<AiSectionCheckPrerequisite> findBySectionCheck_IdOrderBySortOrderAsc(
            Long sectionCheckId);
}
