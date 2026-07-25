package com.wevo.backend.issue.repository;

import com.wevo.backend.issue.domain.IssueAnswer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueAnswerRepository extends JpaRepository<IssueAnswer, Long> {

    Optional<IssueAnswer> findByIssue_Id(Long issueId);

    List<IssueAnswer> findAllByIssue_SynthesisSet_Id(Long synthesisSetId);

    @Query("SELECT a FROM IssueAnswer a "
            + "JOIN FETCH a.issue i JOIN FETCH i.synthesisSet s "
            + "WHERE s.id = :synthesisSetId")
    List<IssueAnswer> findAllWithIssueBySynthesisSetId(
            @Param("synthesisSetId") Long synthesisSetId);

    @Query("SELECT a FROM IssueAnswer a "
            + "JOIN FETCH a.issue i JOIN FETCH i.synthesisSet s "
            + "WHERE a.id IN :answerIds")
    List<IssueAnswer> findAllWithIssueByIdIn(@Param("answerIds") List<Long> answerIds);
}
