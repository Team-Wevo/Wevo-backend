package com.wevo.backend.issue.repository;

import com.wevo.backend.issue.domain.IssueAnswer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueAnswerRepository extends JpaRepository<IssueAnswer, Long> {

    Optional<IssueAnswer> findByIssue_Id(Long issueId);

    List<IssueAnswer> findAllByIssue_SynthesisSet_Id(Long synthesisSetId);
}
