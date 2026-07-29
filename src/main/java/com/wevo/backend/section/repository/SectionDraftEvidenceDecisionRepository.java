package com.wevo.backend.section.repository;

import com.wevo.backend.section.domain.SectionDraftEvidenceDecision;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionDraftEvidenceDecisionRepository
        extends JpaRepository<SectionDraftEvidenceDecision, Long> {

    List<SectionDraftEvidenceDecision> findAllByDraftEvidence_IdOrderBySortOrderAsc(Long evidenceId);
}
