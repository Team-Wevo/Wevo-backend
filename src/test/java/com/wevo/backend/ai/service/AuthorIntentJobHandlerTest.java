package com.wevo.backend.ai.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wevo.backend.ai.context.AiContextAssembler;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.SectionAccessGuard;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class AuthorIntentJobHandlerTest {

    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final String HASH = "a".repeat(64);

    @Mock private AiJobService aiJobService;
    @Mock private AiJobRepository aiJobRepository;
    @Mock private SectionAccessGuard sectionAccessGuard;
    @Mock private ProjectAccessGuard projectAccessGuard;
    @Mock private AiContextAssembler contextAssembler;
    @Mock private ObjectProvider<AuthorIntentExtractor> extractorProvider;
    @Mock private AuthorIntentResultWriter resultWriter;
    @Mock private AiUsageResultLinkService usageResultLinkService;
    @Mock private AiJobHeartbeatService heartbeatService;
    @Mock private AiJob job;

    @Test
    void heartbeatRegistrationFailureDoesNotLeaveTheJobRunning() {
        AuthorIntentJobHandler handler = new AuthorIntentJobHandler(
                aiJobService, aiJobRepository, sectionAccessGuard, projectAccessGuard,
                contextAssembler, extractorProvider, resultWriter, usageResultLinkService,
                heartbeatService);
        given(aiJobRepository.findByRequestIdWithExecutionContext(REQUEST_ID))
                .willReturn(Optional.of(job));
        given(job.getRequestId()).willReturn(REQUEST_ID);
        given(job.getInputSnapshotHash()).willReturn(HASH);
        given(aiJobService.start(REQUEST_ID, HASH))
                .willReturn(new AiJobStartResult(REQUEST_ID, AiJobStatus.RUNNING, true));
        given(heartbeatService.start(REQUEST_ID, AiFeature.AUTHOR_INTENT_EXTRACTION))
                .willThrow(new RejectedExecutionException("scheduler shutdown"));

        handler.run(REQUEST_ID);

        verify(aiJobService).fail(eq(REQUEST_ID), any(RejectedExecutionException.class));
        verify(extractorProvider, never()).getIfAvailable();
    }
}
