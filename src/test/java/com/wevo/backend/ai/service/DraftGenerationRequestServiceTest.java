package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.context.AiBaseDraftContext;
import com.wevo.backend.ai.context.AiDraftSynthesisContext;
import com.wevo.backend.ai.context.AiInputSnapshot;
import com.wevo.backend.ai.context.AiProjectBrief;
import com.wevo.backend.ai.context.AiProjectIdentity;
import com.wevo.backend.ai.context.AiSectionContext;
import com.wevo.backend.ai.context.AiTemplateContext;
import com.wevo.backend.ai.context.AssembledAiContext;
import com.wevo.backend.ai.context.DraftGenerationContext;
import com.wevo.backend.ai.context.AiContextAssembler;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.service.UserService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DraftGenerationRequestServiceTest {

    private static final long SECTION_ID = 2L;
    private static final long USER_ID = 3L;
    private static final long PROJECT_ID = 1L;
    private static final String HASH = "a".repeat(64);

    @Mock private SectionAccessGuard sectionAccessGuard;
    @Mock private ProjectAccessGuard projectAccessGuard;
    @Mock private AiContextAssembler contextAssembler;
    @Mock private AiJobService aiJobService;
    @Mock private UserService userService;
    @Mock private ProjectSection section;
    @Mock private Project project;
    @Mock private VerifiedProjectAccess access;
    @Mock private User user;

    private DraftGenerationRequestService service;

    @BeforeEach
    void setUp() {
        service = new DraftGenerationRequestService(
                sectionAccessGuard,
                projectAccessGuard,
                contextAssembler,
                aiJobService,
                properties(),
                userService
        );
        given(sectionAccessGuard.requireOwnedSection(SECTION_ID, USER_ID)).willReturn(section);
        given(section.getProject()).willReturn(project);
        given(section.getId()).willReturn(SECTION_ID);
        given(project.getId()).willReturn(PROJECT_ID);
        given(projectAccessGuard.requireParticipantAccess(PROJECT_ID, USER_ID)).willReturn(access);
        given(userService.getUserReference(USER_ID)).willReturn(user);
    }

    @Test
    void queuesValidResolvedCurrentSynthesis() {
        UUID requestId = UUID.randomUUID();
        given(contextAssembler.assembleDraftGeneration(access, SECTION_ID))
                .willReturn(assembled(context(ProjectSectionStatus.SYNTHESIZING, false, false, 4, 4)));
        given(aiJobService.findLatest(any())).willReturn(Optional.empty());
        given(aiJobService.createOrGet(any()))
                .willReturn(result(requestId, AiJobStatus.QUEUED));

        assertThat(service.requestDraftGeneration(SECTION_ID, USER_ID)).isEqualTo(requestId);
        verify(aiJobService).createOrGet(any());
    }

    @Test
    void reusesQueuedOrSucceededSameSnapshot() {
        UUID requestId = UUID.randomUUID();
        given(contextAssembler.assembleDraftGeneration(access, SECTION_ID))
                .willReturn(assembled(context(ProjectSectionStatus.SYNTHESIZING, false, false, 4, 4)));
        given(aiJobService.findLatest(any()))
                .willReturn(Optional.of(result(requestId, AiJobStatus.SUCCEEDED)));

        assertThat(service.requestDraftGeneration(SECTION_ID, USER_ID)).isEqualTo(requestId);
        verify(aiJobService, never()).createOrGet(any());
    }

    @Test
    void blocksWrongStatusStaleGenerationAndUnresolvedConflictBeforeQueue() {
        assertRejected(
                context(ProjectSectionStatus.DRAFTING, false, false, 4, 4),
                ErrorCode.INVALID_SECTION_STATUS_TRANSITION
        );
        assertRejected(
                context(ProjectSectionStatus.SYNTHESIZING, true, false, 4, 4),
                ErrorCode.CONFLICT
        );
        assertRejected(
                context(ProjectSectionStatus.SYNTHESIZING, false, false, 4, 5),
                ErrorCode.CONFLICT
        );
        assertRejected(
                context(ProjectSectionStatus.SYNTHESIZING, false, true, 4, 4),
                ErrorCode.ISSUE_CONFLICT_UNDECIDED
        );
    }

    private void assertRejected(DraftGenerationContext context, ErrorCode code) {
        given(contextAssembler.assembleDraftGeneration(access, SECTION_ID))
                .willReturn(assembled(context));
        given(aiJobService.findLatest(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.requestDraftGeneration(SECTION_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(code));
    }

    private AssembledAiContext<DraftGenerationContext> assembled(
            DraftGenerationContext context
    ) {
        return new AssembledAiContext<>(context, new AiInputSnapshot(new byte[]{1}, HASH));
    }

    private DraftGenerationContext context(
            ProjectSectionStatus status,
            boolean stale,
            boolean unresolved,
            long sectionGeneration,
            long synthesisGeneration
    ) {
        return new DraftGenerationContext(
                new AiProjectIdentity(PROJECT_ID, "프로젝트", OutputType.PROPOSAL),
                new AiProjectBrief(null, null, null),
                new AiSectionContext(
                        SECTION_ID,
                        "문제",
                        1,
                        status,
                        sectionGeneration,
                        stale,
                        new AiTemplateContext("problem", null, null)
                ),
                new AiDraftSynthesisContext(
                        10L,
                        synthesisGeneration,
                        "합의",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        unresolved
                ),
                new AiBaseDraftContext(0, null),
                List.of()
        );
    }

    private AiJobCreateResult result(UUID requestId, AiJobStatus status) {
        return new AiJobCreateResult(requestId, status, null, 1, false, false);
    }

    private AiProperties properties() {
        return new AiProperties(
                "none",
                new AiProperties.ModelOptions(
                        "model",
                        Duration.ofSeconds(1),
                        48_000,
                        1_000,
                        60_000,
                        1_000,
                        AiProperties.ModelOptions.CONSERVATIVE_CHAR_V1,
                        AiProperties.ModelOptions.REJECT_OVERSIZED_INPUT_V1,
                        0,
                        Duration.ZERO,
                        Duration.ZERO
                ),
                Map.of(),
                null
        );
    }
}
