package com.wevo.backend.issue.repository;

import com.wevo.backend.issue.domain.IssueDecision;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IssueDecisionRepository extends JpaRepository<IssueDecision, Long> {

    Optional<IssueDecision> findByIssue_Id(Long issueId);

    /**
     * 세트에 속한 모든 CONFLICT 결정을 한 번에 조회한다. (세트 단위 배치 조회 — N+1 방지)
     *
     * <p>선택지 결정은 응답에 선택지 <b>본문</b>이 필요하므로 {@code selectedOption}까지 함께 로딩한다.
     * 직접 입력 결정은 선택지가 없으므로 {@code LEFT JOIN}이다.
     */
    @Query("SELECT d FROM IssueDecision d JOIN FETCH d.issue i LEFT JOIN FETCH d.selectedOption "
            + "WHERE i.synthesisSet.id = :synthesisSetId")
    List<IssueDecision> findAllWithIssueBySynthesisSetId(@Param("synthesisSetId") Long synthesisSetId);
}
