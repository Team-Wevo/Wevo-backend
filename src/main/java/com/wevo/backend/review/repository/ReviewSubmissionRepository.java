package com.wevo.backend.review.repository;

import com.wevo.backend.review.domain.ReviewSubmission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewSubmissionRepository extends JpaRepository<ReviewSubmission, Long> {

    /**
     * 특정 섹션에 달린 외부 검토 제출을 최신순으로 조회한다. (팀장 결과 조회용)
     *
     * <p>검토 링크는 외부 전용이므로 섹션 기준으로 조회하면 곧 외부 검토 제출이다.
     */
    List<ReviewSubmission> findByReviewLink_ProjectSection_IdOrderByCreatedAtDesc(Long projectSectionId);

    /**
     * 링크에 쌓인 제출 수. (20개 상한 검사용 — 링크 행 락 안에서 호출)
     */
    long countByReviewLink_Id(Long reviewLinkId);

    /**
     * 같은 브라우저(익명 검토자 키)가 이미 이 링크에 제출했는지. (브라우저당 1회 검사용)
     */
    boolean existsByReviewLink_IdAndAnonymousReviewerId(Long reviewLinkId, String anonymousReviewerId);
}
