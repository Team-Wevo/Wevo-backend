package com.wevo.backend.ai.service;

import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiJobServiceGuardrailTest {

    private AiJobRepository repository;
    private AiJobPersistenceService persistenceService;
    private AiJobIdempotencyKeyGenerator keyGenerator;
    private AiGuardrailService guardrailService;
    private AiJobService service;

    @BeforeEach
    void setUp() {
        repository = mock(AiJobRepository.class);
        persistenceService = mock(AiJobPersistenceService.class);
        keyGenerator = mock(AiJobIdempotencyKeyGenerator.class);
        guardrailService = mock(AiGuardrailService.class);
        when(keyGenerator.generate(any())).thenReturn("a".repeat(64));
        service = new AiJobService(
                repository,
                persistenceService,
                keyGenerator,
                mock(AiErrorClassifier.class),
                mock(AiErrorMessageSanitizer.class),
                mock(AiExecutionAvailabilityGuard.class),
                guardrailService,
                mock(AiGuardrailLifecycleService.class),
                Clock.fixed(Instant.parse("2026-08-02T03:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void quotaFailureOccursBeforeJobPersistence() {
        AiJobCreateCommand command = command();
        when(repository.findTopByIdempotencyKeyOrderByExecutionSequenceDesc("a".repeat(64)))
                .thenReturn(Optional.empty());
        when(guardrailService.reserve(any()))
                .thenThrow(new BusinessException(ErrorCode.AI_GUARDRAIL_UNAVAILABLE));

        assertThatThrownBy(() -> service.createOrGet(command))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.AI_GUARDRAIL_UNAVAILABLE));

        verify(persistenceService, never()).insert(any());
    }

    @Test
    void idempotentJobReuseDoesNotReserveQuotaAgain() {
        AiJob existing = mock(AiJob.class);
        when(existing.getRequestId()).thenReturn(UUID.randomUUID());
        when(existing.getStatus()).thenReturn(AiJobStatus.QUEUED);
        when(existing.getExecutionSequence()).thenReturn(1);
        when(repository.findTopByIdempotencyKeyOrderByExecutionSequenceDesc("a".repeat(64)))
                .thenReturn(Optional.of(existing));

        service.createOrGet(command());

        verify(guardrailService, never()).reserve(any());
        verify(persistenceService, never()).insert(any());
    }

    private AiJobCreateCommand command() {
        Project project = mock(Project.class);
        User user = mock(User.class);
        when(project.getId()).thenReturn(1L);
        when(user.getId()).thenReturn(2L);
        return new AiJobCreateCommand(
                project,
                null,
                user,
                AiFeature.DRAFT_GENERATION,
                "b".repeat(64),
                "source-v1",
                "prompt-v1",
                "schema-v1",
                "test-model",
                10);
    }
}
