package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.context.AiInputSnapshot;
import com.wevo.backend.ai.context.AiProjectBrief;
import com.wevo.backend.ai.context.AiProjectIdentity;
import com.wevo.backend.ai.context.AssembledAiContext;
import com.wevo.backend.ai.context.ProjectFlowReviewContext;
import com.wevo.backend.ai.context.ProjectFlowReviewContextAssembler;
import com.wevo.backend.ai.context.ProjectFlowReviewSectionContext;
import com.wevo.backend.ai.context.AiTemplateContext;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.section.service.SectionConfirmationQueryService;
import com.wevo.backend.section.service.SectionConfirmationSummary;
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
class ProjectFlowReviewRequestServiceTest {
    @Mock ProjectAccessGuard accessGuard;
    @Mock SectionConfirmationQueryService confirmationQuery;
    @Mock ProjectFlowReviewContextAssembler assembler;
    @Mock AiJobService jobService;
    @Mock UserService userService;
    @Mock VerifiedProjectAccess access;
    @Mock Project project;
    @Mock User user;
    @Mock ProjectMember membership;
    private ProjectFlowReviewRequestService service;
    private static final String HASH = "a".repeat(64);

    @BeforeEach
    void setUp() {
        service = new ProjectFlowReviewRequestService(accessGuard, confirmationQuery, assembler,
                jobService, properties(), userService);
        given(accessGuard.requireOwner(1L, 7L)).willReturn(membership);
        given(accessGuard.requireParticipantAccess(1L, 7L)).willReturn(access);
        given(access.project()).willReturn(project);
        given(project.getId()).willReturn(1L);
        given(confirmationQuery.getConfirmationSummary(access)).willReturn(new SectionConfirmationSummary(2, 2));
        given(assembler.assemble(access)).willReturn(assembled());
    }

    @Test
    void createsProjectScopedJobAndReusesSuccessfulSnapshot() {
        UUID first = UUID.randomUUID();
        given(jobService.findLatest(any())).willReturn(Optional.empty());
        given(userService.getUserReference(7L)).willReturn(user);
        given(user.getId()).willReturn(7L);
        given(jobService.createOrGet(any())).willReturn(new AiJobCreateResult(
                first, AiJobStatus.QUEUED, null, 1, true, false));
        assertThat(service.requestReview(1L, 7L)).isEqualTo(first);

        UUID reused = UUID.randomUUID();
        given(jobService.findLatest(any())).willReturn(Optional.of(new AiJobCreateResult(
                reused, AiJobStatus.SUCCEEDED, 10L, 1, false, false)));
        assertThat(service.requestReview(1L, 7L)).isEqualTo(reused);
    }

    @Test
    void rejectsIncompleteProjectBeforeCreatingJob() {
        given(confirmationQuery.getConfirmationSummary(access)).willReturn(new SectionConfirmationSummary(2, 1));
        assertThatThrownBy(() -> service.requestReview(1L, 7L))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
        verify(jobService, never()).createOrGet(any());
        verify(assembler, never()).assemble(any());
    }

    @Test
    void memberIsForbiddenAndNonMemberIsHiddenAsProjectNotFound() {
        doThrow(new BusinessException(ErrorCode.FORBIDDEN)).when(accessGuard).requireOwner(1L, 7L);
        assertCode(ErrorCode.FORBIDDEN);
        doThrow(new BusinessException(ErrorCode.NOT_PROJECT_MEMBER)).when(accessGuard).requireOwner(1L, 7L);
        assertCode(ErrorCode.PROJECT_NOT_FOUND);
    }

    private void assertCode(ErrorCode code) {
        assertThatThrownBy(() -> service.requestReview(1L, 7L)).isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(code));
    }
    private AssembledAiContext<ProjectFlowReviewContext> assembled() {
        var sections = List.of(section(1L, 1), section(2L, 2));
        return new AssembledAiContext<>(new ProjectFlowReviewContext(
                new AiProjectIdentity(1L, "프로젝트", OutputType.PROPOSAL),
                new AiProjectBrief(null, null, "고객"), 2, 2, sections, List.of()),
                new AiInputSnapshot(new byte[]{1}, HASH));
    }
    private ProjectFlowReviewSectionContext section(Long id, int order) {
        return new ProjectFlowReviewSectionContext(id, "key-" + id, order, 1, "제목",
                new AiTemplateContext("key-" + id, null, null), "본문 " + id);
    }
    private AiProperties properties() {
        return new AiProperties("none", new AiProperties.ModelOptions(
                "test-model", Duration.ofSeconds(1), 100_000, 4_096, 120_000, 8_000,
                AiProperties.ModelOptions.CONSERVATIVE_CHAR_V1,
                AiProperties.ModelOptions.REJECT_OVERSIZED_INPUT_V1,
                0, Duration.ZERO, Duration.ZERO), Map.of(), null);
    }
}
