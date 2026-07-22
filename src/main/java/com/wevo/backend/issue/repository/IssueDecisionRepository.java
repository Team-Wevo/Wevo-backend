package com.wevo.backend.issue.repository;

import com.wevo.backend.issue.domain.IssueDecision;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueDecisionRepository extends JpaRepository<IssueDecision, Long> {

    Optional<IssueDecision> findByIssue_Id(Long issueId);
}
