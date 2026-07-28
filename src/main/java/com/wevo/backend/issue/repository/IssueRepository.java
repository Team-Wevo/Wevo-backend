package com.wevo.backend.issue.repository;

import com.wevo.backend.issue.domain.Issue;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IssueRepository extends JpaRepository<Issue, Long> {

    List<Issue> findAllBySynthesisSet_IdOrderBySortOrderAsc(Long synthesisSetId);

    /**
     * 결정·답변처럼 쟁점 상태를 변경하는 경로에서 대상 쟁점을 배타 잠금으로 조회한다.
     *
     * <p>정리 세트를 함께 로딩해 현재 세트 여부와 섹션 소속을 추가 쿼리 없이 검증한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Issue i JOIN FETCH i.synthesisSet WHERE i.id = :id")
    Optional<Issue> findByIdForUpdate(@Param("id") Long id);
}
