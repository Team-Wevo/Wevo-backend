package com.wevo.backend.issue.repository;

import com.wevo.backend.issue.domain.EvidenceRequest;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceRequestRepository extends JpaRepository<EvidenceRequest, Long> {

    Optional<EvidenceRequest> findByIssue_Id(Long issueId);
}
