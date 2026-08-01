package com.wevo.backend.ai.repository;

import com.wevo.backend.ai.domain.OpinionClusterMember;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OpinionClusterMemberRepository extends JpaRepository<OpinionClusterMember, Long> {

    @Query("select member from OpinionClusterMember member "
            + "join fetch member.cluster cluster "
            + "where member.clusterSetId = :clusterSetId "
            + "order by cluster.sortOrder, member.sortOrder")
    List<OpinionClusterMember> findAllForSet(@Param("clusterSetId") Long clusterSetId);
}
