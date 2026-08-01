package com.wevo.backend.opinion.repository;

import com.wevo.backend.opinion.domain.Opinion;
import com.wevo.backend.opinion.domain.OpinionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OpinionRepository extends JpaRepository<Opinion, Long> {

    /**
     * 특정 섹션에서 특정 사용자가 작성한 의견을 조회한다. (멤버당 섹션당 1개)
     */
    Optional<Opinion> findByProjectSection_IdAndAuthor_Id(Long projectSectionId, Long authorId);

    boolean existsByProjectSection_IdAndStatus(Long projectSectionId, OpinionStatus status);

    boolean existsByProjectSection_IdAndAuthor_IdAndStatus(
            Long projectSectionId, Long authorId, OpinionStatus status);

    /**
     * 섹션의 특정 상태 의견 목록을 작성자와 함께 제출 시각 순으로 조회한다.
     *
     * <p>목록 응답이 작성자 정보(id·이름·프로필)를 항상 포함하므로 {@code JOIN FETCH} 로
     * 미리 로딩해 의견 수만큼 작성자 조회가 나가는 N+1 을 방지한다.
     */
    @Query("SELECT o FROM Opinion o JOIN FETCH o.author "
            + "WHERE o.projectSection.id = :projectSectionId AND o.status = :status "
            + "ORDER BY o.submittedAt ASC, o.id ASC")
    List<Opinion> findAllWithAuthorByProjectSectionIdAndStatus(
            @Param("projectSectionId") Long projectSectionId,
            @Param("status") OpinionStatus status);

    /**
     * AI 내부 입력용 제출 의견. 사용자 공개 gate와 작성자 profile fetch를 사용하지 않으며,
     * project 소속을 쿼리에서 제한하고 동률까지 결정적으로 정렬한다.
     */
    @Query("SELECT o FROM Opinion o "
            + "WHERE o.projectSection.id = :sectionId "
            + "AND o.projectSection.project.id = :projectId "
            + "AND o.status = com.wevo.backend.opinion.domain.OpinionStatus.SUBMITTED "
            + "ORDER BY o.submittedAt ASC, o.id ASC")
    List<Opinion> findSubmittedForAiContext(
            @Param("projectId") Long projectId,
            @Param("sectionId") Long sectionId);
}
