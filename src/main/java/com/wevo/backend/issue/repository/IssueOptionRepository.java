package com.wevo.backend.issue.repository;

import com.wevo.backend.issue.domain.IssueOption;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueOptionRepository extends JpaRepository<IssueOption, Long> {

    List<IssueOption> findAllByIssue_IdOrderBySortOrderAsc(Long issueId);
}
