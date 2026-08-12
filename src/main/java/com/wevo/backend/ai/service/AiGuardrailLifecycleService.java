package com.wevo.backend.ai.service;

import com.wevo.backend.ai.config.AiGuardrailRecoveryProperties;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.domain.AiRequestStatus;
import com.wevo.backend.ai.domain.AiUsageLog;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.ai.repository.AiUsageLogRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** DB 감사 로그를 정본으로 Redis usage와 terminal job 정산을 멱등 재생한다. */
@Service
public class AiGuardrailLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(AiGuardrailLifecycleService.class);
    private static final EnumSet<AiJobStatus> TERMINAL_STATUSES = EnumSet.of(
            AiJobStatus.SUCCEEDED,
            AiJobStatus.FAILED,
            AiJobStatus.CANCELLED,
            AiJobStatus.STALE
    );

    private final AiJobRepository jobRepository;
    private final AiUsageLogRepository usageLogRepository;
    private final AiJobPersistenceService jobPersistenceService;
    private final AiGuardrailService guardrailService;
    private final AiGuardrailRecoveryProperties recoveryProperties;
    private final Clock clock;

    public AiGuardrailLifecycleService(
            AiJobRepository jobRepository,
            AiUsageLogRepository usageLogRepository,
            AiJobPersistenceService jobPersistenceService,
            AiGuardrailService guardrailService,
            AiGuardrailRecoveryProperties recoveryProperties,
            Clock clock
    ) {
        this.jobRepository = jobRepository;
        this.usageLogRepository = usageLogRepository;
        this.jobPersistenceService = jobPersistenceService;
        this.guardrailService = guardrailService;
        this.recoveryProperties = recoveryProperties;
        this.clock = clock;
    }

    /** job 생성 커밋 뒤 pending reservation index에서 제거한다. 실패하면 복구 scheduler가 대조한다. */
    public void confirmJobCreatedQuietly(AiJob job) {
        try {
            guardrailService.confirmJobCreated(job);
        } catch (RuntimeException exception) {
            log.warn(
                    "AI guardrail job 생성 확인을 복구 작업으로 이관합니다. requestId={}, exceptionType={}",
                    job.getRequestId(), exception.getClass().getSimpleName());
        }
    }

    /** terminal 전이 호출 경로를 실패시키지 않고 즉시 재정산을 한 번 시도한다. */
    public void reconcileQuietly(UUID requestId) {
        try {
            reconcile(requestId);
        } catch (RuntimeException exception) {
            log.warn(
                    "AI guardrail 정산을 복구 작업으로 이관합니다. requestId={}, exceptionType={}",
                    requestId, exception.getClass().getSimpleName());
        }
    }

    public boolean reconcile(UUID requestId) {
        AiJob job = jobRepository.findByRequestId(requestId).orElse(null);
        if (job == null || !job.isTerminal() || job.getGuardrailSettledAt() != null) {
            return false;
        }
        if (!guardrailService.isEnabled()) {
            jobPersistenceService.markGuardrailSettled(requestId, LocalDateTime.now(clock));
            return true;
        }

        List<AiUsageLog> logs = usageLogRepository.findAllByAiJob_IdOrderByIdAsc(job.getId());
        if (logs.stream().anyMatch(log -> log.getRequestStatus() == AiRequestStatus.REQUESTED)) {
            return false;
        }
        for (AiUsageLog usageLog : logs) {
            guardrailService.recordUsage(job, usageLog.getRequestId(), usageLog.costSnapshot());
        }
        guardrailService.settle(job);
        jobPersistenceService.markGuardrailSettled(requestId, LocalDateTime.now(clock));
        return true;
    }

    public int recoverUnsettledJobs() {
        if (!recoveryProperties.isEnabled() || !guardrailService.isEnabled()) {
            return 0;
        }
        int recovered = 0;
        List<UUID> requestIds = jobRepository.findGuardrailUnsettledTerminalRequestIds(
                TERMINAL_STATUSES,
                PageRequest.of(0, recoveryProperties.batchSize())
        );
        for (UUID requestId : requestIds) {
            try {
                if (reconcile(requestId)) {
                    recovered++;
                }
            } catch (RuntimeException exception) {
                log.warn(
                        "미정산 AI guardrail 복구 실패·건너뜀. requestId={}, exceptionType={}",
                        requestId, exception.getClass().getSimpleName());
            }
        }
        return recovered;
    }

    public int recoverOrphanReservations() {
        if (!recoveryProperties.isEnabled() || !guardrailService.isEnabled()) {
            return 0;
        }
        Instant threshold = Instant.now(clock).minus(recoveryProperties.orphanGrace());
        int recovered = 0;
        for (AiGuardrailPendingReservation reservation : guardrailService.findPendingReservations(
                threshold, recoveryProperties.batchSize())) {
            try {
                if (jobRepository.existsByRequestId(reservation.requestId())) {
                    guardrailService.confirmJobCreated(reservation);
                } else {
                    guardrailService.rollbackOrphan(reservation);
                }
                recovered++;
            } catch (RuntimeException exception) {
                log.warn(
                        "고아 AI guardrail 예약 복구 실패·건너뜀. requestId={}, exceptionType={}",
                        reservation.requestId(), exception.getClass().getSimpleName());
            }
        }
        return recovered;
    }
}
