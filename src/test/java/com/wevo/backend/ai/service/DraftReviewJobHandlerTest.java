package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wevo.backend.ai.config.AiJobDispatchProperties;
import com.wevo.backend.ai.context.AiContextAssembler;
import com.wevo.backend.ai.context.AiInputSnapshot;
import com.wevo.backend.ai.context.AiProjectIdentity;
import com.wevo.backend.ai.context.AiSectionContext;
import com.wevo.backend.ai.context.AiTemplateContext;
import com.wevo.backend.ai.context.AssembledAiContext;
import com.wevo.backend.ai.context.DraftReviewContext;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.dto.model.DraftReviewOutput;
import com.wevo.backend.ai.dto.model.DraftReviewRewriteOutput;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.service.SectionPrecheckStateService;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DraftReviewJobHandlerTest {

    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final String HASH = "a".repeat(64);
    private static final String CHANGED_HASH = "b".repeat(64);

    @Mock private AiJobService aiJobService;
    @Mock private AiJobRepository aiJobRepository;
    @Mock private ProjectAccessGuard projectAccessGuard;
    @Mock private AiContextAssembler contextAssembler;
    @Mock private ObjectProvider<SectionDraftReviewer> reviewerProvider;
    @Mock private SectionDraftReviewer reviewer;
    @Mock private PrecheckResultWriter resultWriter;
    @Mock private AiUsageResultLinkService usageResultLinkService;
    @Mock private SectionPrecheckStateService precheckStateService;
    @Mock private AiJob job;
    @Mock private Project project;
    @Mock private ProjectSection section;
    @Mock private User user;
    @Mock private VerifiedProjectAccess access;

    private ScheduledExecutorService scheduler;
    private DraftReviewJobHandler handler;
    private DraftReviewContext context;

    @BeforeEach
    void setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        handler = new DraftReviewJobHandler(
                aiJobService,
                aiJobRepository,
                projectAccessGuard,
                contextAssembler,
                reviewerProvider,
                resultWriter,
                usageResultLinkService,
                precheckStateService,
                new AiJobHeartbeatService(aiJobService, scheduler,
                        new AiJobDispatchProperties(
                                true, Duration.ofSeconds(2), 20, 8,
                                Duration.ofSeconds(10), Duration.ofSeconds(60),
                                Duration.ofSeconds(60)))
        );
        context = context();
        given(aiJobRepository.findByRequestIdWithExecutionContext(REQUEST_ID))
                .willReturn(Optional.of(job));
        given(job.getRequestId()).willReturn(REQUEST_ID);
        given(job.getId()).willReturn(90L);
        given(job.getInputSnapshotHash()).willReturn(HASH);
        given(job.getProject()).willReturn(project);
        given(job.getProjectSection()).willReturn(section);
        given(job.getRequestedBy()).willReturn(user);
        given(project.getId()).willReturn(1L);
        given(section.getId()).willReturn(2L);
        given(user.getId()).willReturn(3L);
        given(projectAccessGuard.requireParticipantAccess(1L, 3L)).willReturn(access);
        given(contextAssembler.assembleDraftReview(access, 2L))
                .willReturn(assembled(HASH));
        given(reviewerProvider.getIfAvailable()).willReturn(reviewer);
        given(aiJobService.start(REQUEST_ID, HASH))
                .willReturn(new AiJobStartResult(
                        REQUEST_ID, AiJobStatus.RUNNING, true));
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    void storesSuccessfulResultAndLinksUsageAfterLockedSnapshotProbe() {
        DraftReviewOutput output = output();
        given(reviewer.review(job, context)).willReturn(output);
        given(resultWriter.persist(REQUEST_ID, context, output)).willReturn(44L);
        given(aiJobService.succeed(eq(REQUEST_ID), any(), any()))
                .willAnswer(invocation -> {
                    AiJobSnapshotProbe probe = invocation.getArgument(1);
                    assertThat(probe.currentSnapshotHash()).isEqualTo(HASH);
                    AiJobResultWriter writer = invocation.getArgument(2);
                    return new AiJobCompletionResult(
                            REQUEST_ID, AiJobStatus.SUCCEEDED, writer.persist(), true);
                });

        handler.run(REQUEST_ID);

        verify(precheckStateService).lockInputSections(access, 2L);
        verify(resultWriter).persist(REQUEST_ID, context, output);
        verify(usageResultLinkService).linkSuccessfulInvocations(90L, 44L);
        verify(aiJobService, never()).fail(any(), any());
    }

    @Test
    void changedInputAtCompletionDiscardsOutputWithoutResultWrite() {
        given(contextAssembler.assembleDraftReview(access, 2L))
                .willReturn(assembled(HASH), assembled(CHANGED_HASH));
        given(reviewer.review(job, context)).willReturn(output());
        given(aiJobService.succeed(eq(REQUEST_ID), any(), any()))
                .willAnswer(invocation -> {
                    AiJobSnapshotProbe probe = invocation.getArgument(1);
                    assertThat(probe.currentSnapshotHash()).isEqualTo(CHANGED_HASH);
                    return new AiJobCompletionResult(
                            REQUEST_ID, AiJobStatus.STALE, null, false);
                });

        handler.run(REQUEST_ID);

        verify(precheckStateService).lockInputSections(access, 2L);
        verify(resultWriter, never()).persist(any(), any(), any());
        verify(usageResultLinkService, never())
                .linkSuccessfulInvocations(any(), any());
    }

    @Test
    void providerFailureFailsJobAndPreservesExistingResult() {
        given(reviewer.review(job, context))
                .willThrow(new RuntimeException("provider"));

        handler.run(REQUEST_ID);

        verify(aiJobService).fail(eq(REQUEST_ID), any(RuntimeException.class));
        verify(resultWriter, never()).persist(any(), any(), any());
    }

    private AssembledAiContext<DraftReviewContext> assembled(String hash) {
        return new AssembledAiContext<>(
                context, new AiInputSnapshot(new byte[]{1}, hash));
    }

    private DraftReviewOutput output() {
        return new DraftReviewOutput(
                List.of(),
                new DraftReviewRewriteOutput("현재 본문", 0));
    }

    private DraftReviewContext context() {
        return new DraftReviewContext(
                new AiProjectIdentity(1L, "프로젝트", OutputType.PROPOSAL),
                new AiSectionContext(
                        2L, "문제", 1, ProjectSectionStatus.DRAFTING,
                        0, false,
                        new AiTemplateContext("problem", "설명", "가이드")),
                30L,
                3,
                "현재 본문",
                List.of(),
                "0123456789abcdef"
        );
    }
}
