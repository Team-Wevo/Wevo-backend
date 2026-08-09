package com.wevo.backend.review.repository;

import com.wevo.backend.review.domain.TeamReview;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamReviewRepository extends JpaRepository<TeamReview, Long> {

    /**
     * 섹션에 제출된 팀 검토 전체. (검토 현황 집계용)
     *
     * <p>검토자 이름은 팀원 로스터에서 읽으므로 여기서는 reviewer 를 fetch 하지 않아도 된다.
     * ({@code reviewer.getId()} 는 프록시에서 추가 쿼리 없이 얻는다.)
     */
    List<TeamReview> findByProjectSection_Id(Long projectSectionId);

    /**
     * 섹션에 제출된 검토 중 <b>검토자가 탈퇴하지 않은</b> 것만. (확정 조건 판정용)
     *
     * <p>{@link #findByProjectSection_Id} 와 달리 탈퇴한 검토자의 행을 뺀다. 팀 검토 현황 목록(§6.1)은
     * 팀원 로스터를 기준으로 만들어져 탈퇴자의 검토가 노출되지 않는데, 확정 조건(§6.3)이 그 행을
     * 계속 세면 <b>팀장이 목록에서 볼 수도 해소할 수도 없는 수정 요청이 확정을 영영 막는다.</b>
     * 목록과 판정이 같은 집합을 보도록 여기서 맞춘다.
     *
     * <p>프로젝트에서 멤버를 빼는 API 는 없으므로 "로스터에서 빠졌다"는 곧 "탈퇴했다"와 같다.
     * 멤버 제외 기능이 생기면 이 조회도 함께 바꿔야 한다.
     */
    @Query("SELECT tr FROM TeamReview tr "
            + "WHERE tr.projectSection.id = :projectSectionId "
            + "AND tr.reviewer.status <> com.wevo.backend.user.domain.UserStatus.WITHDRAWN")
    List<TeamReview> findActiveReviewerReviewsByProjectSectionId(
            @Param("projectSectionId") Long projectSectionId);

    /**
     * 특정 팀원이 이 섹션에 남긴 검토. (내 검토 업서트용)
     */
    Optional<TeamReview> findByProjectSection_IdAndReviewer_Id(Long projectSectionId, Long reviewerUserId);
}
