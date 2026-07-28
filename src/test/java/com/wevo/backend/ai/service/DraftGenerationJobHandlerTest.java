package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wevo.backend.ai.config.AiJobDispatchProperties;
import com.wevo.backend.ai.context.AiBaseDraftContext;
import com.wevo.backend.ai.context.AiContextAssembler;
import com.wevo.backend.ai.context.AiDraftSynthesisContext;
import com.wevo.backend.ai.context.AiInputSnapshot;
import com.wevo.backend.ai.context.AiProjectBrief;
import com.wevo.backend.ai.context.AiProjectIdentity;
import com.wevo.backend.ai.context.AiSectionContext;
import com.wevo.backend.ai.context.AiTemplateContext;
import com.wevo.backend.ai.context.AssembledAiContext;
import com.wevo.backend.ai.context.DraftGenerationContext;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.service.AiSectionDraftCreateCommand;
import com.wevo.backend.section.service.AiSectionDraftWriter;
import com.wevo.backend.section.service.SectionSynthesisStateService;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DraftGenerationJobHandlerTest {

    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final String HASH = "a".repeat(64);
    private static final String CHANGED_HASH = "b".repeat(64);

    @Mock private AiJobService aiJobService;
    @Mock private AiJobRepository aiJobRepository;
    @Mock private ProjectAccessGuard projectAccessGuard;
    @Mock private AiContextAssembler contextAssembler;
    @Mock private ObjectProvider<SectionDraftGenerator> generatorProvider;
    @Mock private SectionDraftGenerator generator;
    @Mock private AiSectionDraftWriter draftWriter;
    @Mock private AiUsageResultLinkService usageResultLinkService;
    @Mock private SectionSynthesisStateService sectionStateService;
    @Mock private AiJob job;
    @Mock private Project project;
    @Mock private ProjectSection section;
    @Mock private User user;
    @Mock private VerifiedProjectAccess access;

    private ScheduledExecutorService scheduler;
    private DraftGenerationJobHandler handler;
    private DraftGenerationContext context;

    @BeforeEach
    void setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        handler = new DraftGenerationJobHandler(
                aiJobService,
                aiJobRepository,
                projectAccessGuard,
                contextAssembler,
                generatorProvider,
                draftWriter,
                usageResultLinkService,
                sectionStateService,
                scheduler,
                new AiJobDispatchProperties(
                        true, Duration.ofSeconds(2), 20, 8,
                        Duration.ofSeconds(10), Duration.ofSeconds(60), Duration.ofSeconds(60))
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
        given(job.getSourceVersion()).willReturn(context.sourceVersion());
        given(project.getId()).willReturn(1L);
        given(section.getId()).willReturn(2L);
        given(user.getId()).willReturn(3L);
        given(projectAccessGuard.requireParticipantAccess(1L, 3L)).willReturn(access);
        given(contextAssembler.assembleDraftGenerationSnapshot(access, 2L))
                .willReturn(assembled(HASH));
        given(generatorProvider.getIfAvailable()).willReturn(generator);
        given(aiJobService.start(REQUEST_ID, HASH))
                .willReturn(new AiJobStartResult(REQUEST_ID, AiJobStatus.RUNNING, true));
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    void persistsDraftAndLinksJobAndUsageInsideCompletionWriter() {
        given(generator.generate(job, context)).willReturn(output());
        given(draftWriter.createAiDraft(any())).willReturn(44L);
        given(aiJobService.succeed(eq(REQUEST_ID), any(), any())).willAnswer(invocation -> {
            AiJobSnapshotProbe probe = invocation.getArgument(1);
            assertThat(probe.currentSnapshotHash()).isEqualTo(HASH);
            AiJobResultWriter writer = invocation.getArgument(2);
            return new AiJobCompletionResult(
                    REQUEST_ID, AiJobStatus.SUCCEEDED, writer.persist(), true);
        });

        handler.run(REQUEST_ID);

        ArgumentCaptor<AiSectionDraftCreateCommand> command =
                ArgumentCaptor.forClass(AiSectionDraftCreateCommand.class);
        verify(draftWriter).createAiDraft(command.capture());
        assertThat(command.getValue().content()).isEqualTo("생성 본문");
        assertThat(command.getValue().baseVersion()).isZero();
        assertThat(command.getValue().synthesisSetId()).isEqualTo(10L);
        verify(sectionStateService).lockForResultCommit(2L);
        verify(usageResultLinkService).linkSuccessfulInvocations(90L, 44L);
        verify(aiJobService, never()).fail(any(), any());
    }

    @Test
    void changedSnapshotMarksStaleWithoutProviderOrDraftWrite() {
        given(contextAssembler.assembleDraftGenerationSnapshot(access, 2L))
                .willReturn(assembled(CHANGED_HASH));

        handler.run(REQUEST_ID);

        verify(aiJobService).markStale(REQUEST_ID);
        verify(generator, never()).generate(any(), any());
        verify(draftWriter, never()).createAiDraft(any());
    }

    @Test
    void providerFailureFailsJobWithoutChangingDraft() {
        given(generator.generate(job, context)).willThrow(new RuntimeException("provider"));

        handler.run(REQUEST_ID);

        verify(aiJobService).fail(eq(REQUEST_ID), any(RuntimeException.class));
        verify(draftWriter, never()).createAiDraft(any());
    }

    private AssembledAiContext<DraftGenerationContext> assembled(String hash) {
        return new AssembledAiContext<>(context, new AiInputSnapshot(new byte[]{1}, hash));
    }

    private DraftGenerationOutput output() {
        return new DraftGenerationOutput(
                "생성 본문", List.of(), List.of(), List.of(), List.of());
    }

    private DraftGenerationContext context() {
        return new DraftGenerationContext(
                new AiProjectIdentity(1L, "프로젝트", OutputType.PROPOSAL),
                new AiProjectBrief(null, null, null),
                new AiSectionContext(
                        2L, "문제", 1, ProjectSectionStatus.SYNTHESIZING,
                        0, false, new AiTemplateContext("problem", null, null)),
                new AiDraftSynthesisContext(
                        10L, 0, "합의", List.of(), List.of(), List.of(), List.of(), false),
                new AiBaseDraftContext(0, null),
                List.of()
        );
    }
}
