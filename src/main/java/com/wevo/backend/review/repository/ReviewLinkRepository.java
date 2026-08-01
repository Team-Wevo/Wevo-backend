package com.wevo.backend.review.repository;

import com.wevo.backend.review.domain.ReviewLink;
import com.wevo.backend.review.domain.ReviewLinkStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewLinkRepository extends JpaRepository<ReviewLink, Long> {

    /**
     * 외부 검토 링크를 토큰 해시로 조회한다. (열람용 — 읽기 전용)
     */
    Optional<ReviewLink> findByTokenHash(String tokenHash);

    /**
     * 외부 검토 링크를 토큰 해시로 조회하되 <b>행에 쓰기 락</b>을 건다. (제출용)
     *
     * <p>같은 링크에 대한 동시 제출을 직렬화해, 20개 상한 검사(count 후 insert)와
     * 브라우저당 1회 검사가 레이스 없이 수행되도록 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from ReviewLink l where l.tokenHash = :tokenHash")
    Optional<ReviewLink> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    /**
     * 외부 검토 링크를 ID 로 조회하되 <b>행에 쓰기 락</b>을 건다. (팀장의 수동 종료용)
     *
     * <p>{@link #findByTokenHashForUpdate}(제출)와 같은 행을 잠그므로, 링크를 닫는 도중에 제출이
     * 한 건 더 들어와 종료 시점이 흐려지는 경합을 막는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from ReviewLink l where l.id = :id")
    Optional<ReviewLink> findByIdForUpdate(@Param("id") Long id);

    /**
     * 특정 섹션에 걸린 특정 상태의 링크들을 조회한다.
     *
     * <p>본문 수정 시 {@code ACTIVE} 링크 만료 처리와, 상태 복구 조회에서 함께 쓴다.
     * 섹션당 {@code ACTIVE} 링크는 부분 유니크 인덱스
     * ({@code uk_review_links_active_per_section})로 최대 1개가 보장되므로,
     * {@code ACTIVE} 조회 결과의 첫 건이 곧 현재 활성 링크다.
     */
    List<ReviewLink> findByProjectSection_IdAndStatus(Long projectSectionId, ReviewLinkStatus status);

    /**
     * 섹션의 {@code ACTIVE} 링크들을 조회하되 <b>행에 쓰기 락</b>을 건다. (본문 수정 시 만료 처리용)
     *
     * <p>잠금 없이 읽으면 <b>변경 손실</b>이 난다 — 팀장의 수동 종료({@code ReviewLinkService#updateStatus})는
     * 섹션이 아니라 <b>링크</b> 행을 잠그므로 섹션 잠금으로는 직렬화되지 않는다. 만료 대상을 잠금 없이
     * 읽으면 아직 커밋되지 않은 종료를 못 보고 {@code ACTIVE} 로 읽은 뒤, 낡은 값 위에서
     * {@code ReviewLink#markOutdated()} 의 가드를 통과해 방금 커밋된 {@code CLOSED} 를
     * {@code OUTDATED} 로 덮어쓴다. 잠금을 걸면 종료가 커밋될 때까지 기다렸다가 다시 읽어
     * {@code CLOSED} 를 보고 만료 대상에서 제외한다.
     * <p>상태 복구 조회처럼 쓰기가 이어지지 않는 경로는 잠금 없는
     * {@link #findByProjectSection_IdAndStatus} 를 그대로 쓴다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from ReviewLink l where l.projectSection.id = :projectSectionId and l.status = :status")
    List<ReviewLink> findByProjectSection_IdAndStatusForUpdate(
            @Param("projectSectionId") Long projectSectionId,
            @Param("status") ReviewLinkStatus status);
}
