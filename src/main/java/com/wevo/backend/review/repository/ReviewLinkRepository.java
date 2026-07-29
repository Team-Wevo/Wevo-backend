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
     * 특정 섹션에 걸린 특정 상태의 링크들을 조회한다.
     *
     * <p>본문 수정 시 {@code ACTIVE} 링크 만료 처리와, 상태 복구 조회에서 함께 쓴다.
     * 섹션당 {@code ACTIVE} 링크는 부분 유니크 인덱스
     * ({@code uk_review_links_active_per_section})로 최대 1개가 보장되므로,
     * {@code ACTIVE} 조회 결과의 첫 건이 곧 현재 활성 링크다.
     */
    List<ReviewLink> findByProjectSection_IdAndStatus(Long projectSectionId, ReviewLinkStatus status);
}
