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

    long countByStatus(AiJobStatus status);

    Optional<AiJob> findFirstByStatusOrderByStartedAtAsc(AiJobStatus status);

    Optional<AiJob> findByRequestId(UUID requestId);

    @Query("""
            select job from AiJob job
            join fetch job.project
            left join fetch job.projectSection
            join fetch job.requestedBy
            where job.requestId = :requestId
            """)
    Optional<AiJob> findByRequestIdWithExecutionContext(@Param("requestId") UUID requestId);

    Optional<AiJob> findTopByIdempotencyKeyOrderByExecutionSequenceDesc(String idempotencyKey);

    /**
     * 섹션의 해당 기능 <b>최신 실행</b>을 조회한다. (API_SPEC §3.8.2 {@code latestJob})
     *
     * <p>멱등키 기준으로는 부족하다 — 의견 재오픈으로 마감 세대가 바뀌면 입력 스냅샷도 달라져
     * 멱등키가 갈리므로, 섹션 전체에서 가장 최근에 큐잉된 실행을 찾아야 한다.
     */
    Optional<AiJob> findTopByProjectSection_IdAndFeatureOrderByQueuedAtDescIdDesc(
            Long projectSectionId,
            AiFeature feature
    );

    Optional<AiJob> findTopByProjectSection_IdAndFeatureAndSourceVersionOrderByQueuedAtDescIdDesc(
            Long projectSectionId,
            AiFeature feature,
            String sourceVersion
    );

    /**
     * 섹션의 해당 기능 <b>최신 성공 실행</b>을 조회한다. 현재 정리 세트({@code resultId})의 출처다.
     *
     * <p>대체(supersede)는 성공 시에만 일어나므로(§3.8.1), 진행 중·실패한 재실행이 있어도
     * 이 실행의 결과가 현재 세트로 남는다. 정렬 기준은 큐잉 순서가 아니라 <b>완료 시각</b>이다 —
     * 세트가 만들어지는 시점이 곧 대체 시점이기 때문이다.
     */
    Optional<AiJob> findTopByProjectSection_IdAndFeatureAndStatusOrderByCompletedAtDescIdDesc(
            Long projectSectionId,
            AiFeature feature,
            AiJobStatus status
    );

    Optional<AiJob> findTopByProject_IdAndFeatureOrderByQueuedAtDescIdDesc(
            Long projectId, AiFeature feature);

    Optional<AiJob> findTopByProject_IdAndFeatureAndStatusOrderByCompletedAtDescIdDesc(
            Long projectId, AiFeature feature, AiJobStatus status);

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
