package com.wevo.backend.ai.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wevo.backend.ai.config.AiJobDispatchProperties;
import com.wevo.backend.ai.context.AiInputSnapshot;
import com.wevo.backend.ai.context.AiProjectBrief;
import com.wevo.backend.ai.context.AiProjectIdentity;
import com.wevo.backend.ai.context.AiTemplateContext;
import com.wevo.backend.ai.context.AssembledAiContext;
import com.wevo.backend.ai.context.ProjectFlowReviewContext;
import com.wevo.backend.ai.context.ProjectFlowReviewContextAssembler;
import com.wevo.backend.ai.context.ProjectFlowReviewSectionContext;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.dto.model.ProjectFlowReviewOutput;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.section.service.SectionConfirmationQueryService;
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
class ProjectFlowReviewJobHandlerTest {
    private static final UUID ID = UUID.randomUUID();
    private static final String HASH = "a".repeat(64);
    @Mock AiJobService jobService; @Mock AiJobRepository jobRepository;
    @Mock ProjectAccessGuard accessGuard; @Mock SectionConfirmationQueryService confirmationQuery;
    @Mock ProjectFlowReviewContextAssembler assembler;
    @Mock ObjectProvider<ProjectFlowReviewer> reviewerProvider; @Mock ProjectFlowReviewer reviewer;
    @Mock ProjectFlowReviewResultWriter writer; @Mock AiUsageResultLinkService usageLink;
    @Mock AiJob job; @Mock Project project; @Mock User user; @Mock VerifiedProjectAccess access;
    private ScheduledExecutorService scheduler;
    private ProjectFlowReviewJobHandler handler;

    @BeforeEach
    void setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        handler = new ProjectFlowReviewJobHandler(jobService, jobRepository, accessGuard,
                confirmationQuery, assembler, reviewerProvider, writer, usageLink, scheduler,
                new AiJobDispatchProperties(true, Duration.ofSeconds(2), 20, 8,
                        Duration.ofSeconds(15), Duration.ofSeconds(60), Duration.ofSeconds(60)));
        given(jobRepository.findByRequestIdWithExecutionContext(ID)).willReturn(Optional.of(job));
        given(job.getRequestId()).willReturn(ID); given(job.getInputSnapshotHash()).willReturn(HASH);
        given(job.getProject()).willReturn(project); given(project.getId()).willReturn(1L);
        given(job.getRequestedBy()).willReturn(user); given(user.getId()).willReturn(7L);
        given(accessGuard.requireParticipantAccess(1L, 7L)).willReturn(access);
        given(jobService.start(ID, HASH)).willReturn(new AiJobStartResult(ID, AiJobStatus.RUNNING, true));
    }

    @AfterEach void tearDown() { scheduler.shutdownNow(); }

    @Test
    void changedInputBeforeProviderIsMarkedStale() {
        given(assembler.assemble(access)).willReturn(assembled("b".repeat(64)));
        handler.run(ID);
        verify(jobService).markStale(ID);
        verify(reviewer, never()).review(any(), any());
    }

    @Test
    void completionLocksAllSectionsRechecksSnapshotAndPersists() {
        var assembled = assembled(HASH);
        var output = new ProjectFlowReviewOutput(List.of());
        given(assembler.assemble(access)).willReturn(assembled);
        given(reviewerProvider.getIfAvailable()).willReturn(reviewer);
        given(reviewer.review(job, assembled.context())).willReturn(output);
        given(writer.persist(any())).willReturn(50L);
        given(job.getId()).willReturn(11L);
        given(jobService.succeed(eq(ID), any(), any())).willAnswer(invocation -> {
            AiJobSnapshotProbe probe = invocation.getArgument(1);
            probe.currentSnapshotHash();
            AiJobResultWriter resultWriter = invocation.getArgument(2);
            return new AiJobCompletionResult(ID, AiJobStatus.SUCCEEDED, resultWriter.persist(), true);
        });
        handler.run(ID);
        verify(confirmationQuery).lockAllForUpdate(access);
        verify(writer).persist(any(ProjectFlowReviewPersistCommand.class));
    }

    private AssembledAiContext<ProjectFlowReviewContext> assembled(String hash) {
        var section = new ProjectFlowReviewSectionContext(10L, "problem", 1, 1, "문제",
                new AiTemplateContext("problem", null, null), "확정 본문");
        var context = new ProjectFlowReviewContext(new AiProjectIdentity(1L, "프로젝트", OutputType.PROPOSAL),
                new AiProjectBrief(null, null, "고객"), 1, 1, List.of(section), List.of());
        return new AssembledAiContext<>(context, new AiInputSnapshot(new byte[]{1}, hash));
    }
}
