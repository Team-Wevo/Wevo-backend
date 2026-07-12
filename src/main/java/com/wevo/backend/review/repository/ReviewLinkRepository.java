package com.wevo.backend.review.repository;

import com.wevo.backend.review.domain.ReviewLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReviewLinkRepository extends JpaRepository<ReviewLink, Long> {

    /**
     * 외부 검토 링크 토큰으로 조회한다.
     */
    Optional<ReviewLink> findByToken(String token);
}
