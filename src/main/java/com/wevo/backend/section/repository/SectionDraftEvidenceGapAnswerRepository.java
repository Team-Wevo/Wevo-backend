package com.wevo.backend.section.repository;

import com.wevo.backend.section.domain.SectionDraftEvidenceGapAnswer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionDraftEvidenceGapAnswerRepository
        extends JpaRepository<SectionDraftEvidenceGapAnswer, Long> {

    List<SectionDraftEvidenceGapAnswer> findAllByDraftEvidence_IdOrderBySortOrderAsc(Long evidenceId);
}
