package com.wevo.backend.ai.repository;

import com.wevo.backend.ai.domain.OpinionCluster;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpinionClusterRepository extends JpaRepository<OpinionCluster, Long> {
    List<OpinionCluster> findAllByClusterSet_IdOrderBySortOrder(Long clusterSetId);
}
