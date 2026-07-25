package com.wevo.backend.issue.repository;

import com.wevo.backend.issue.domain.SynthesisSet;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SynthesisSetRepository extends JpaRepository<SynthesisSet, Long> {

    boolean existsByProjectSectionId(Long projectSectionId);

    Optional<SynthesisSet> findByRequestId(UUID requestId);

    Optional<SynthesisSet> findTopByProjectSectionIdOrderByCreatedAtDescIdDesc(Long projectSectionId);
}
