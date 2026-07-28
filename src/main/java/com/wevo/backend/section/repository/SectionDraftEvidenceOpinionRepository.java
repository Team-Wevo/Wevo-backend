package com.wevo.backend.section.repository;

import com.wevo.backend.section.domain.SectionDraftEvidenceOpinion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionDraftEvidenceOpinionRepository
        extends JpaRepository<SectionDraftEvidenceOpinion, Long> {

    List<SectionDraftEvidenceOpinion> findAllByDraftEvidence_IdOrderBySortOrderAsc(Long evidenceId);
}
