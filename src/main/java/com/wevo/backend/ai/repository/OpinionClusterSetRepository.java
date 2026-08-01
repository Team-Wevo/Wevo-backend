package com.wevo.backend.ai.repository;

import com.wevo.backend.ai.domain.OpinionClusterSet;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpinionClusterSetRepository extends JpaRepository<OpinionClusterSet, Long> {
    Optional<OpinionClusterSet> findBySourceJob_Id(Long sourceJobId);
}
