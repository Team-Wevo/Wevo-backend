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
}
