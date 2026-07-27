package com.wevo.backend.issue.repository;

import com.wevo.backend.issue.domain.IssueOption;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IssueOptionRepository extends JpaRepository<IssueOption, Long> {

    List<IssueOption> findAllByIssue_IdOrderBySortOrderAsc(Long issueId);

    /**
     * 세트에 속한 모든 쟁점의 선택지를 한 번에 조회한다. 쟁점 수만큼 쿼리가 늘지 않게 하기 위한
     * 세트 단위 배치 조회이며, 정렬은 쟁점 순서 → 선택지 순서다.
     */
    @Query("SELECT o FROM IssueOption o JOIN FETCH o.issue i "
            + "WHERE i.synthesisSet.id = :synthesisSetId "
            + "ORDER BY i.sortOrder ASC, o.sortOrder ASC")
    List<IssueOption> findAllWithIssueBySynthesisSetId(@Param("synthesisSetId") Long synthesisSetId);
}
