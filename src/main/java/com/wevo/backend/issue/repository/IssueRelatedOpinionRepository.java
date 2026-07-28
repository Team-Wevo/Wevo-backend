package com.wevo.backend.issue.repository;

import com.wevo.backend.issue.domain.IssueRelatedOpinion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IssueRelatedOpinionRepository extends JpaRepository<IssueRelatedOpinion, Long> {

    List<IssueRelatedOpinion> findAllByIssue_IdOrderBySortOrderAsc(Long issueId);

    Optional<IssueRelatedOpinion> findFirstByIssue_IdAndAuthorUserIdOrderBySortOrderAsc(
            Long issueId, Long authorUserId);

    /**
     * 세트에 속한 모든 쟁점의 관련 의견을 한 번에 조회한다. (세트 단위 배치 조회 — N+1 방지)
     */
    @Query("SELECT r FROM IssueRelatedOpinion r JOIN FETCH r.issue i "
            + "WHERE i.synthesisSet.id = :synthesisSetId "
            + "ORDER BY i.sortOrder ASC, r.sortOrder ASC")
    List<IssueRelatedOpinion> findAllWithIssueBySynthesisSetId(
            @Param("synthesisSetId") Long synthesisSetId);
}
