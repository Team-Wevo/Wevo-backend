package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.wevo.backend.ai.config.AiGuardrailRecoveryProperties;
import com.wevo.backend.ai.domain.AiCostSnapshot;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiRequestStatus;
import com.wevo.backend.ai.domain.AiUsageLog;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.ai.repository.AiUsageLogRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiGuardrailLifecycleServiceTest {

    private AiJobRepository jobRepository;
    private AiUsageLogRepository usageLogRepository;
    private AiJobPersistenceService persistenceService;
    private AiGuardrailService guardrailService;
    private AiGuardrailLifecycleService service;
    private AiJob job;
    private AiUsageLog usageLog;
    private UUID jobRequestId;
    private UUID usageRequestId;
    private AiCostSnapshot cost;

    @BeforeEach
    void setUp() {
        jobRepository = mock(AiJobRepository.class);
        usageLogRepository = mock(AiUsageLogRepository.class);
        persistenceService = mock(AiJobPersistenceService.class);
        guardrailService = mock(AiGuardrailService.class);
        service = new AiGuardrailLifecycleService(
                jobRepository,
                usageLogRepository,
                persistenceService,
                guardrailService,
                new AiGuardrailRecoveryProperties(
                        true, Duration.ofMinutes(1), Duration.ofMinutes(5), 100),
                Clock.fixed(Instant.parse("2026-08-12T03:00:00Z"), ZoneOffset.UTC)
        );
        job = mock(AiJob.class);
        usageLog = mock(AiUsageLog.class);
        jobRequestId = UUID.randomUUID();
        usageRequestId = UUID.randomUUID();
        cost = AiCostSnapshot.unpriced("test-v1");
        given(guardrailService.isEnabled()).willReturn(true);
        given(jobRepository.findByRequestId(jobRequestId)).willReturn(Optional.of(job));
        given(job.getId()).willReturn(1L);
        given(job.isTerminal()).willReturn(true);
        given(usageLogRepository.findAllByAiJob_IdOrderByIdAsc(1L))
                .willReturn(List.of(usageLog));
        given(usageLog.getRequestStatus()).willReturn(AiRequestStatus.SUCCEEDED);
        given(usageLog.getRequestId()).willReturn(usageRequestId);
        given(usageLog.costSnapshot()).willReturn(cost);
    }

    @Test
    void usageRecordFailureLeavesJobPendingAndNextRunReplaysBeforeSettlement() {
        doThrow(new IllegalStateException("redis unavailable"))
                .doNothing()
                .when(guardrailService).recordUsage(job, usageRequestId, cost);

        assertThatThrownBy(() -> service.reconcile(jobRequestId))
                .isInstanceOf(IllegalStateException.class);
        assertThat(service.reconcile(jobRequestId)).isTrue();

        verify(guardrailService, times(2))
                .recordUsage(job, usageRequestId, cost);
        verify(guardrailService).settle(job);
        verify(persistenceService).markGuardrailSettled(
                org.mockito.ArgumentMatchers.eq(jobRequestId),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void settlementFailureReplaysUsageAndSettlesOnNextRun() {
        doThrow(new IllegalStateException("redis unavailable"))
                .doNothing()
                .when(guardrailService).settle(job);

        assertThatThrownBy(() -> service.reconcile(jobRequestId))
                .isInstanceOf(IllegalStateException.class);
        assertThat(service.reconcile(jobRequestId)).isTrue();

        verify(guardrailService, times(2))
                .recordUsage(job, usageRequestId, cost);
        verify(guardrailService, times(2)).settle(job);
        verify(persistenceService).markGuardrailSettled(
                org.mockito.ArgumentMatchers.eq(jobRequestId),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void requestedAuditLogDefersSettlementUntilAuditRecoveryCompletes() {
        given(usageLog.getRequestStatus()).willReturn(AiRequestStatus.REQUESTED);

        assertThat(service.reconcile(jobRequestId)).isFalse();

        verify(guardrailService, never()).recordUsage(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(guardrailService, never()).settle(job);
        verify(persistenceService, never()).markGuardrailSettled(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void orphanRecoveryConfirmsPersistedJobAndRollsBackOnlyMissingJob() {
        AiGuardrailPendingReservation persisted = new AiGuardrailPendingReservation(
                "ledger:persisted", UUID.randomUUID());
        AiGuardrailPendingReservation orphan = new AiGuardrailPendingReservation(
                "ledger:orphan", UUID.randomUUID());
        given(guardrailService.findPendingReservations(
                Instant.parse("2026-08-12T02:55:00Z"), 100))
                .willReturn(List.of(persisted, orphan));
        given(jobRepository.existsByRequestId(persisted.requestId())).willReturn(true);
        given(jobRepository.existsByRequestId(orphan.requestId())).willReturn(false);

        assertThat(service.recoverOrphanReservations()).isEqualTo(2);

        verify(guardrailService).confirmJobCreated(persisted);
        verify(guardrailService).rollbackOrphan(orphan);
        verify(guardrailService, never()).rollbackOrphan(persisted);
    }
}
