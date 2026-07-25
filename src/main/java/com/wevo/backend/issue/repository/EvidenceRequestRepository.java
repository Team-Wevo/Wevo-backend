package com.wevo.backend.issue.repository;

import com.wevo.backend.issue.domain.EvidenceRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EvidenceRequestRepository extends JpaRepository<EvidenceRequest, Long> {

    Optional<EvidenceRequest> findByIssue_Id(Long issueId);

    /**
     * 세트에 속한 모든 GAP 근거 요청을 한 번에 조회한다. (세트 단위 배치 조회 — N+1 방지)
     */
    @Query("SELECT r FROM EvidenceRequest r JOIN FETCH r.issue i "
            + "WHERE i.synthesisSet.id = :synthesisSetId")
    List<EvidenceRequest> findAllWithIssueBySynthesisSetId(@Param("synthesisSetId") Long synthesisSetId);
}
