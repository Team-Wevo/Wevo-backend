package com.wevo.backend.ai.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wevo.backend.ai.config.AiJobDispatchProperties;
import com.wevo.backend.ai.context.AiContextAssembler;
import com.wevo.backend.ai.context.AiInputSnapshot;
import com.wevo.backend.ai.context.AiOpinionContext;
import com.wevo.backend.ai.context.AiTemplateContext;
import com.wevo.backend.ai.context.AssembledAiContext;
import com.wevo.backend.ai.context.OpinionClusteringContext;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.dto.model.OpinionClusterOutput;
import com.wevo.backend.ai.dto.model.OpinionClusteringOutput;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.user.domain.User;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class OpinionClusteringJobHandlerTest {

    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final String HASH = "a".repeat(64);

    @Mock private AiJobService aiJobService;
    @Mock private AiJobRepository jobRepository;
    @Mock private SectionAccessGuard sectionAccessGuard;
    @Mock private ProjectAccessGuard projectAccessGuard;
    @Mock private AiContextAssembler contextAssembler;
    @Mock private ObjectProvider<OpinionClusterer> clustererProvider;
    @Mock private OpinionClusterer clusterer;
    @Mock private OpinionClusteringResultWriter resultWriter;
    @Mock private AiUsageResultLinkService usageResultLinkService;
    @Mock private AiJob job;
    @Mock private Project project;
    @Mock private ProjectSection section;
    @Mock private User user;
    @Mock private VerifiedProjectAccess access;

    private ScheduledExecutorService scheduler;
    private OpinionClusteringJobHandler handler;

    @BeforeEach
    void setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        handler = new OpinionClusteringJobHandler(
                aiJobService, jobRepository, sectionAccessGuard, projectAccessGuard,
                contextAssembler, clustererProvider, resultWriter, usageResultLinkService,
                scheduler, new AiJobDispatchProperties(
                        true, Duration.ofSeconds(2), 20, 8,
                        Duration.ofSeconds(15), Duration.ofSeconds(60), Duration.ofSeconds(60)));
        given(jobRepository.findByRequestIdWithExecutionContext(REQUEST_ID))
                .willReturn(Optional.of(job));
        given(job.getRequestId()).willReturn(REQUEST_ID);
        given(job.getInputSnapshotHash()).willReturn(HASH);
        given(job.getProject()).willReturn(project);
        given(job.getProjectSection()).willReturn(section);
        given(job.getRequestedBy()).willReturn(user);
        given(project.getId()).willReturn(20L);
        given(section.getId()).willReturn(10L);
        given(user.getId()).willReturn(30L);
        given(projectAccessGuard.requireParticipantAccess(20L, 30L)).willReturn(access);
        given(aiJobService.start(REQUEST_ID, HASH))
                .willReturn(new AiJobStartResult(REQUEST_ID, AiJobStatus.RUNNING, true));
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    void changedInputAfterClaimIsMarkedStaleWithoutProviderCall() {
        given(contextAssembler.assembleOpinionClustering(access, 10L))
                .willReturn(assembled("b".repeat(64)));

        handler.run(REQUEST_ID);

        verify(aiJobService).markStale(REQUEST_ID);
        verify(clusterer, never()).cluster(any(), any());
    }

    @Test
    void successfulOutputIsPersistedInsideCompletionWriter() {
        AssembledAiContext<OpinionClusteringContext> assembled = assembled(HASH);
        OpinionClusteringOutput output = new OpinionClusteringOutput(List.of(
                new OpinionClusterOutput(1, "공통", "공통 요약", List.of(1L, 2L, 3L))));
        given(contextAssembler.assembleOpinionClustering(access, 10L)).willReturn(assembled);
        given(clustererProvider.getIfAvailable()).willReturn(clusterer);
        given(clusterer.cluster(job, assembled.context())).willReturn(output);
        given(resultWriter.persist(any())).willReturn(50L);
        given(aiJobService.succeed(eq(REQUEST_ID), any(), any())).willAnswer(invocation -> {
            AiJobSnapshotProbe probe = invocation.getArgument(1);
            probe.currentSnapshotHash();
            AiJobResultWriter writer = invocation.getArgument(2);
            return new AiJobCompletionResult(
                    REQUEST_ID, AiJobStatus.SUCCEEDED, writer.persist(), true);
        });

        handler.run(REQUEST_ID);

        verify(sectionAccessGuard).requireOwnedSectionForUpdate(10L, 30L);
        verify(resultWriter).persist(any(OpinionClusteringPersistCommand.class));
    }

    private AssembledAiContext<OpinionClusteringContext> assembled(String hash) {
        OpinionClusteringContext context = new OpinionClusteringContext(
                20L, 10L, "문제 정의", 1,
                new AiTemplateContext("problem", null, null),
                List.of(
                        new AiOpinionContext(1L, "member-1", "첫 의견", "2026-08-01T10:00:00"),
                        new AiOpinionContext(2L, "member-2", "둘 의견", "2026-08-01T10:01:00"),
                        new AiOpinionContext(3L, "member-3", "셋 의견", "2026-08-01T10:02:00")));
        return new AssembledAiContext<>(context, new AiInputSnapshot(new byte[]{1}, hash));
    }
}
