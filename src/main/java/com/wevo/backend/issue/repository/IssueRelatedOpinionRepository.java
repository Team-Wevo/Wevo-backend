package com.wevo.backend.issue.repository;

import com.wevo.backend.issue.domain.IssueRelatedOpinion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueRelatedOpinionRepository extends JpaRepository<IssueRelatedOpinion, Long> {

    List<IssueRelatedOpinion> findAllByIssue_IdOrderBySortOrderAsc(Long issueId);

    boolean existsByIssue_IdAndAuthorUserId(Long issueId, Long authorUserId);
}
