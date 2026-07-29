package com.wevo.backend.issue.repository;

import com.wevo.backend.issue.domain.SynthesisConsensusEvidence;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SynthesisConsensusEvidenceRepository
        extends JpaRepository<SynthesisConsensusEvidence, Long> {

    List<SynthesisConsensusEvidence> findAllBySynthesisSet_IdOrderBySortOrderAsc(Long synthesisSetId);
}
