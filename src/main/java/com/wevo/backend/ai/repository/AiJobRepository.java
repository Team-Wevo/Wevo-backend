package com.wevo.backend.ai.repository;

import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiJobRepository extends JpaRepository<AiJob, Long> {

    Optional<AiJob> findByRequestId(UUID requestId);

    Optional<AiJob> findTopByIdempotencyKeyOrderByExecutionSequenceDesc(String idempotencyKey);

    List<AiJob> findAllByIdempotencyKeyOrderByExecutionSequenceAsc(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from AiJob job where job.requestId = :requestId")
    Optional<AiJob> findByRequestIdForUpdate(@Param("requestId") UUID requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select job from AiJob job
            where job.idempotencyKey = :idempotencyKey
              and job.executionSequence = (
                  select max(latest.executionSequence)
                  from AiJob latest
                  where latest.idempotencyKey = :idempotencyKey
              )
            """)
    Optional<AiJob> findLatestByIdempotencyKeyForUpdate(@Param("idempotencyKey") String idempotencyKey);

    @Query("""
            select job.requestId
            from AiJob job
            where job.status = com.wevo.backend.ai.domain.AiJobStatus.QUEUED
            order by job.createdAt asc
            """)
    List<UUID> findQueuedRequestIds(Pageable pageable);

    @Query("""
            select job.requestId
            from AiJob job
            where job.status = com.wevo.backend.ai.domain.AiJobStatus.QUEUED
              and job.feature in :features
            order by job.createdAt asc
            """)
    List<UUID> findQueuedRequestIdsByFeatureIn(
            @Param("features") Collection<AiFeature> features,
            Pageable pageable
    );

    @Query("""
            select job.requestId
            from AiJob job
            where job.status = :status
              and job.startedAt < :threshold
            """)
    List<UUID> findRequestIdsByStatusAndStartedAtBefore(
            @Param("status") AiJobStatus status,
            @Param("threshold") LocalDateTime threshold
    );

    @Query("""
            select job.requestId
            from AiJob job
            where job.status = :status
              and job.lastHeartbeatAt < :threshold
            """)
    List<UUID> findRequestIdsByStatusAndLastHeartbeatAtBefore(
            @Param("status") AiJobStatus status,
            @Param("threshold") LocalDateTime threshold
    );
}
