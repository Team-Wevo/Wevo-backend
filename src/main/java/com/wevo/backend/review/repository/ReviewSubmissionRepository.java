package com.wevo.backend.review.repository;

import com.wevo.backend.review.domain.ReviewSubmission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewSubmissionRepository extends JpaRepository<ReviewSubmission, Long> {

    /**
     * 특정 섹션에 달린 외부 검토 제출을 최신순으로 조회한다. (팀장 결과 조회용)
     *
     * <p>검토 링크는 외부 전용이므로 섹션 기준으로 조회하면 곧 외부 검토 제출이다.
     * 버전별 집계·항목별 버전 표기가 링크의 {@code contentVersion} 을 읽으므로,
     * 제출 수만큼 링크 조회가 나가는 N+1 을 막기 위해 링크를 fetch join 으로 함께 로딩한다.
     */
    @Query("select s from ReviewSubmission s join fetch s.reviewLink l "
            + "where l.projectSection.id = :projectSectionId order by s.createdAt desc")
    List<ReviewSubmission> findAllWithLinkByProjectSectionId(@Param("projectSectionId") Long projectSectionId);

    /**
     * 링크에 쌓인 제출 수. (20개 상한 검사용 — 링크 행 락 안에서 호출)
     */
    long countByReviewLink_Id(Long reviewLinkId);

    /**
     * 같은 브라우저(익명 검토자 키)가 이미 이 링크에 제출했는지. (브라우저당 1회 검사용)
     */
    boolean existsByReviewLink_IdAndAnonymousReviewerId(Long reviewLinkId, String anonymousReviewerId);
}
