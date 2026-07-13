package com.wevo.backend.opinion.repository;

import com.wevo.backend.opinion.domain.Opinion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpinionRepository extends JpaRepository<Opinion, Long> {

    /**
     * 특정 섹션에서 특정 사용자가 작성한 의견을 조회한다. (멤버당 섹션당 1개)
     */
    Optional<Opinion> findByProjectSection_IdAndAuthor_Id(Long projectSectionId, Long authorId);
}
