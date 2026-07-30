package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.context.AiContextAssembler;
import com.wevo.backend.ai.context.AiInputSnapshot;
import com.wevo.backend.ai.context.AiProjectIdentity;
import com.wevo.backend.ai.context.AiSectionContext;
import com.wevo.backend.ai.context.AiTemplateContext;
import com.wevo.backend.ai.context.AssembledAiContext;
import com.wevo.backend.ai.context.DraftReviewContext;
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
import com.wevo.backend.section.service.SectionAiContextQueryService;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrecheckRequestServiceTest {

    private static final long SECTION_ID = 2L;
    private static final long USER_ID = 3L;
    private static final long PROJECT_ID = 1L;
    private static final String HASH = "a".repeat(64);

    @Mock private SectionAccessGuard sectionAccessGuard;
    @Mock private ProjectAccessGuard projectAccessGuard;
    @Mock private SectionAiContextQueryService sectionQueryService;
    @Mock private AiContextAssembler contextAssembler;
    @Mock private AiJobService aiJobService;
    @Mock private UserService userService;
    @Mock private ProjectSection section;
    @Mock private Project project;
    @Mock private VerifiedProjectAccess access;
    @Mock private User user;

    private PrecheckRequestService service;

    @BeforeEach
    void setUp() {
        service = new PrecheckRequestService(
                sectionAccessGuard,
                projectAccessGuard,
                sectionQueryService,
                contextAssembler,
                aiJobService,
                properties(),
                userService
        );
        given(sectionAccessGuard.requireParticipantSection(SECTION_ID, USER_ID))
                .willReturn(section);
        given(section.getProject()).willReturn(project);
        given(section.getId()).willReturn(SECTION_ID);
        given(project.getId()).willReturn(PROJECT_ID);
        given(projectAccessGuard.requireParticipantAccess(PROJECT_ID, USER_ID))
                .willReturn(access);
        given(userService.getUserReference(USER_ID)).willReturn(user);
    }

    @Test
    void queuesForDraftingAndReviewingMember() {
        for (ProjectSectionStatus status : List.of(
                ProjectSectionStatus.DRAFTING,
                ProjectSectionStatus.REVIEWING)) {
            UUID requestId = UUID.randomUUID();
            given(contextAssembler.assembleDraftReview(access, SECTION_ID))
                    .willReturn(assembled(context(status)));
            given(aiJobService.findLatest(any())).willReturn(Optional.empty());
            given(aiJobService.createOrGet(any()))
                    .willReturn(result(requestId, AiJobStatus.QUEUED));

            assertThat(service.requestPrecheck(SECTION_ID, USER_ID)).isEqualTo(requestId);
        }
    }

    @Test
    void reusesSuccessfulSameSnapshotBeforeStatusValidation() {
        UUID requestId = UUID.randomUUID();
        given(contextAssembler.assembleDraftReview(access, SECTION_ID))
                .willReturn(assembled(context(ProjectSectionStatus.CONFIRMED)));
        given(aiJobService.findLatest(any()))
                .willReturn(Optional.of(result(requestId, AiJobStatus.SUCCEEDED)));

        assertThat(service.requestPrecheck(SECTION_ID, USER_ID)).isEqualTo(requestId);
        verify(aiJobService, never()).createOrGet(any());
    }

    @Test
    void reusesQueuedAndRunningSameSnapshot() {
        given(contextAssembler.assembleDraftReview(access, SECTION_ID))
                .willReturn(assembled(context(ProjectSectionStatus.DRAFTING)));

        for (AiJobStatus status : List.of(AiJobStatus.QUEUED, AiJobStatus.RUNNING)) {
            UUID requestId = UUID.randomUUID();
            given(aiJobService.findLatest(any()))
                    .willReturn(Optional.of(result(requestId, status)));

            assertThat(service.requestPrecheck(SECTION_ID, USER_ID))
                    .isEqualTo(requestId);
        }
        verify(aiJobService, never()).createOrGet(any());
        verify(aiJobService, never()).retry(any(), any());
    }

    @Test
    void retriesFailedSameSnapshotWithNewRequestId() {
        UUID failedRequestId = UUID.randomUUID();
        UUID retryRequestId = UUID.randomUUID();
        given(contextAssembler.assembleDraftReview(access, SECTION_ID))
                .willReturn(assembled(context(ProjectSectionStatus.DRAFTING)));
        given(aiJobService.findLatest(any()))
                .willReturn(Optional.of(result(failedRequestId, AiJobStatus.FAILED)));
        given(aiJobService.retry(failedRequestId, user))
                .willReturn(result(retryRequestId, AiJobStatus.QUEUED));

        assertThat(service.requestPrecheck(SECTION_ID, USER_ID))
                .isEqualTo(retryRequestId);
        verify(aiJobService).retry(failedRequestId, user);
        verify(aiJobService, never()).createOrGet(any());
    }

    @Test
    void rejectsConfirmedAndMissingDraftBeforeQueue() {
        given(contextAssembler.assembleDraftReview(access, SECTION_ID))
                .willReturn(assembled(context(ProjectSectionStatus.CONFIRMED)));
        given(aiJobService.findLatest(any())).willReturn(Optional.empty());

        assertBusinessError(
                () -> service.requestPrecheck(SECTION_ID, USER_ID),
                ErrorCode.INVALID_SECTION_STATUS_TRANSITION);

        given(sectionQueryService.getLatestDraft(access, SECTION_ID))
                .willThrow(new BusinessException(ErrorCode.SECTION_DRAFT_NOT_FOUND));
        assertBusinessError(
                () -> service.requestPrecheck(SECTION_ID, USER_ID),
                ErrorCode.SECTION_DRAFT_NOT_FOUND);
        verify(aiJobService, never()).createOrGet(any());
    }

    private DraftReviewContext context(ProjectSectionStatus status) {
        return new DraftReviewContext(
                new AiProjectIdentity(PROJECT_ID, "프로젝트", OutputType.PROPOSAL),
                new AiSectionContext(
                        SECTION_ID, "문제", 1, status, 0, false,
                        new AiTemplateContext("problem", "설명", "가이드")),
                20L,
                3,
                "현재 초안",
                List.of(),
                "0123456789abcdef"
        );
    }

    private AssembledAiContext<DraftReviewContext> assembled(DraftReviewContext context) {
        return new AssembledAiContext<>(
                context, new AiInputSnapshot(new byte[]{1}, HASH));
    }

    private AiJobCreateResult result(UUID requestId, AiJobStatus status) {
        return new AiJobCreateResult(requestId, status, null, 1, false, false);
    }

    private void assertBusinessError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(expected));
    }

    private AiProperties properties() {
        return new AiProperties(
                "none",
                new AiProperties.ModelOptions(
                        "model",
                        Duration.ofSeconds(1),
                        64_000,
                        1_000,
                        70_000,
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
