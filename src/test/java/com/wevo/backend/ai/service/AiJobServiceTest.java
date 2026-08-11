package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.exception.AiProviderUnavailableException;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.user.domain.User;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiJobServiceTest {

    @Mock private AiJobRepository repository;
    @Mock private AiJobPersistenceService persistenceService;
    @Mock private AiJobIdempotencyKeyGenerator keyGenerator;
    @Mock private AiErrorClassifier errorClassifier;
    @Mock private AiErrorMessageSanitizer sanitizer;
    @Mock private AiExecutionAvailabilityGuard availabilityGuard;
    @Mock private AiGuardrailService guardrailService;
    @Mock private AiGuardrailLifecycleService guardrailLifecycleService;
    @Mock private AiJobCreateCommand command;
    @Mock private AiJob requestedJob;
    @Mock private User requestedBy;

    private AiJobService service;

    @BeforeEach
    void setUp() {
        service = new AiJobService(
                repository,
                persistenceService,
                keyGenerator,
                errorClassifier,
                sanitizer,
                availabilityGuard,
                guardrailService,
                guardrailLifecycleService,
                Clock.systemUTC()
        );
    }

    @Test
    void unavailableProviderPreventsInitialJobPersistence() {
        given(keyGenerator.generate(command.idempotencyInput())).willReturn("key");
        given(repository.findTopByIdempotencyKeyOrderByExecutionSequenceDesc("key"))
                .willReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new AiProviderUnavailableException())
                .when(availabilityGuard).requireAvailable();

        assertThatThrownBy(() -> service.createOrGet(command))
                .isInstanceOf(AiProviderUnavailableException.class)
                .extracting(error -> ((AiProviderUnavailableException) error).getErrorCode())
                .isEqualTo(ErrorCode.AI_PROVIDER_UNAVAILABLE);

        verify(persistenceService, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unavailableProviderPreventsRetryPersistence() {
        UUID requestId = UUID.randomUUID();
        given(requestedBy.getId()).willReturn(1L);
        given(repository.findByRequestId(requestId)).willReturn(Optional.of(requestedJob));
        org.mockito.Mockito.doThrow(new AiProviderUnavailableException())
                .when(availabilityGuard).requireAvailable();

        assertThatThrownBy(() -> service.retry(requestId, requestedBy))
                .isInstanceOf(AiProviderUnavailableException.class)
                .extracting(error -> ((AiProviderUnavailableException) error).getErrorCode())
                .isEqualTo(ErrorCode.AI_PROVIDER_UNAVAILABLE);

        verify(persistenceService, never()).retry(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }
}
