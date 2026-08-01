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
import com.wevo.backend.ai.context.AiOpinionContext;
import com.wevo.backend.ai.context.AiTemplateContext;
import com.wevo.backend.ai.context.AssembledAiContext;
import com.wevo.backend.ai.context.OpinionClusteringContext;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
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
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpinionClusteringRequestServiceTest {

    private static final long SECTION_ID = 10L;
    private static final long PROJECT_ID = 20L;
    private static final long USER_ID = 30L;
    private static final String HASH = "a".repeat(64);

    @Mock private SectionAccessGuard sectionAccessGuard;
    @Mock private ProjectAccessGuard projectAccessGuard;
    @Mock private AiContextAssembler contextAssembler;
    @Mock private AiJobService aiJobService;
    @Mock private UserService userService;
    @Mock private ProjectSection section;
    @Mock private Project project;
    @Mock private VerifiedProjectAccess access;

    private OpinionClusteringRequestService service;

    @BeforeEach
    void setUp() {
        service = new OpinionClusteringRequestService(
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
        given(contextAssembler.assembleOpinionClustering(access, SECTION_ID))
                .willReturn(assembled(3));
    }

    @Test
    void createsJobForClosedGateWithAtLeastThreeOpinions() {
        UUID requestId = UUID.randomUUID();
        given(section.getStatus()).willReturn(ProjectSectionStatus.SYNTHESIZING);
        given(aiJobService.findLatest(any())).willReturn(Optional.empty());
        given(userService.getUserReference(USER_ID)).willReturn(org.mockito.Mockito.mock(User.class));
        given(aiJobService.createOrGet(any())).willReturn(
                new AiJobCreateResult(requestId, AiJobStatus.QUEUED, null, 1, true, false));

        assertThat(service.requestClustering(SECTION_ID, USER_ID)).isEqualTo(requestId);
        verify(aiJobService).createOrGet(any());
    }

    @Test
    void rejectsOpenGateAndDoesNotCallProviderJob() {
        given(section.getStatus()).willReturn(ProjectSectionStatus.COLLECTING);
        given(aiJobService.findLatest(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.requestClustering(SECTION_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_SECTION_STATUS_TRANSITION));
        verify(aiJobService, never()).createOrGet(any());
    }

    @Test
    void rejectsBelowMinimumWithoutCreatingJob() {
        given(section.getStatus()).willReturn(ProjectSectionStatus.SYNTHESIZING);
        given(contextAssembler.assembleOpinionClustering(access, SECTION_ID))
                .willReturn(assembled(2));
        given(aiJobService.findLatest(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.requestClustering(SECTION_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));
        verify(aiJobService, never()).createOrGet(any());
    }

    @Test
    void reusesSameSuccessfulSnapshotBeforeStateAndMinimumChecks() {
        UUID requestId = UUID.randomUUID();
        given(contextAssembler.assembleOpinionClustering(access, SECTION_ID))
                .willReturn(assembled(2));
        given(aiJobService.findLatest(any())).willReturn(Optional.of(
                new AiJobCreateResult(requestId, AiJobStatus.SUCCEEDED, 1L, 1, false, false)));

        assertThat(service.requestClustering(SECTION_ID, USER_ID)).isEqualTo(requestId);
        verify(aiJobService, never()).createOrGet(any());
    }

    @Test
    void retriesFailedJobForSameEligibleSnapshot() {
        UUID failedId = UUID.randomUUID();
        UUID retryId = UUID.randomUUID();
        User user = org.mockito.Mockito.mock(User.class);
        given(section.getStatus()).willReturn(ProjectSectionStatus.SYNTHESIZING);
        given(aiJobService.findLatest(any())).willReturn(Optional.of(
                new AiJobCreateResult(failedId, AiJobStatus.FAILED, null, 1, false, true)));
        given(userService.getUserReference(USER_ID)).willReturn(user);
        given(aiJobService.retry(failedId, user)).willReturn(
                new AiJobCreateResult(retryId, AiJobStatus.QUEUED, null, 2, true, false));

        assertThat(service.requestClustering(SECTION_ID, USER_ID)).isEqualTo(retryId);
        verify(aiJobService).retry(failedId, user);
        verify(aiJobService, never()).createOrGet(any());
    }

    private AssembledAiContext<OpinionClusteringContext> assembled(int count) {
        List<AiOpinionContext> opinions = java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(id -> new AiOpinionContext(
                        (long) id, "member-" + id, "충분한 길이의 제출 의견 " + id,
                        "2026-08-01T10:0" + id + ":00"))
                .toList();
        OpinionClusteringContext context = new OpinionClusteringContext(
                PROJECT_ID, SECTION_ID, "문제 정의", 1,
                new AiTemplateContext("problem", "목적", "가이드"), opinions);
        return new AssembledAiContext<>(context, new AiInputSnapshot(new byte[]{1}, HASH));
    }

    private AiProperties properties() {
        return new AiProperties(
                "none",
                new AiProperties.ModelOptions(
                        "test-model", Duration.ofSeconds(1), 10_000, 1_024,
                        20_000, 1_000,
                        AiProperties.ModelOptions.CONSERVATIVE_CHAR_V1,
                        AiProperties.ModelOptions.REJECT_OVERSIZED_INPUT_V1,
                        0, Duration.ZERO, Duration.ZERO),
                Map.of(), null);
    }
}
