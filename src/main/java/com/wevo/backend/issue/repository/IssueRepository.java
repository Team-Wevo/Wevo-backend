package com.wevo.backend.issue.repository;

import com.wevo.backend.issue.domain.Issue;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueRepository extends JpaRepository<Issue, Long> {

    List<Issue> findAllBySynthesisSet_IdOrderBySortOrderAsc(Long synthesisSetId);
}
