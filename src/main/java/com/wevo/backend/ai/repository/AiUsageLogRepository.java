package com.wevo.backend.ai.repository;

import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiRequestStatus;
import com.wevo.backend.ai.domain.AiUsageLog;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {

    Optional<AiUsageLog> findByRequestId(UUID requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select log from AiUsageLog log where log.requestId = :requestId")
    Optional<AiUsageLog> findByRequestIdForUpdate(@Param("requestId") UUID requestId);

    @Query("""
            select log.requestId
            from AiUsageLog log
            where log.requestStatus = :status
              and log.feature = :feature
              and log.startedAt < :threshold
            """)
    List<UUID> findRequestIdsForOrphanRecovery(
            @Param("status") AiRequestStatus status,
            @Param("feature") AiFeature feature,
            @Param("threshold") LocalDateTime threshold
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AiUsageLog log
            set log.resultId = :resultId
            where log.aiJob.id = :aiJobId
              and log.requestStatus = com.wevo.backend.ai.domain.AiRequestStatus.SUCCEEDED
              and log.resultId is null
            """)
    int linkSuccessfulInvocationsToResult(
            @Param("aiJobId") Long aiJobId,
            @Param("resultId") Long resultId
    );
}
