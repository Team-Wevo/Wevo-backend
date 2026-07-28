package com.wevo.backend.ai.repository;

import com.wevo.backend.ai.domain.AiSectionCheck;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiSectionCheckRepository extends JpaRepository<AiSectionCheck, Long> {

    @Query("""
            select check from AiSectionCheck check
            join fetch check.sourceJob
            where check.id = :id
            """)
    Optional<AiSectionCheck> findByIdWithSourceJob(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select check from AiSectionCheck check
            join fetch check.sourceJob job
            where job.requestId = :requestId
              and check.projectSection.id = :sectionId
            """)
    Optional<AiSectionCheck> findBySectionAndRequestIdForUpdate(
            @Param("sectionId") Long sectionId,
            @Param("requestId") UUID requestId
    );
}
