package com.wevo.backend.section.repository;

import com.wevo.backend.section.domain.SectionDraftEvidence;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionDraftEvidenceRepository extends JpaRepository<SectionDraftEvidence, Long> {

    Optional<SectionDraftEvidence> findByGenerationRequestId(UUID generationRequestId);

    Optional<SectionDraftEvidence>
    findTopBySectionDraft_ProjectSection_IdOrderBySectionDraft_VersionDesc(Long sectionId);
}
