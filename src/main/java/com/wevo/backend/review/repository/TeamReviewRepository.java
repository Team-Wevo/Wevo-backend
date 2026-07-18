package com.wevo.backend.review.repository;

import com.wevo.backend.review.domain.TeamReview;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamReviewRepository extends JpaRepository<TeamReview, Long> {

    /**
     * 섹션에 제출된 팀 검토 전체. (검토 현황 집계용)
     *
     * <p>검토자 이름은 팀원 로스터에서 읽으므로 여기서는 reviewer 를 fetch 하지 않아도 된다.
     * ({@code reviewer.getId()} 는 프록시에서 추가 쿼리 없이 얻는다.)
     */
    List<TeamReview> findByProjectSection_Id(Long projectSectionId);

    /**
     * 특정 팀원이 이 섹션에 남긴 검토. (내 검토 업서트용)
     */
    Optional<TeamReview> findByProjectSection_IdAndReviewer_Id(Long projectSectionId, Long reviewerUserId);
}
