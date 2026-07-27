package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.config.AiProperties.ModelOptions;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.service.SectionAccessGuard;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SynthesisRequestServiceTest {

    private static final long SECTION_ID = 10L;
    private static final long USER_ID = 7L;
    private static final long PROJECT_ID = 100L;
    private static final String HASH = "a".repeat(64);

    @Mock private SectionAccessGuard sectionAccessGuard;
    @Mock private SynthesisSnapshotAssembler snapshotAssembler;
    @Mock private SynthesisInputHasher inputHasher;
    @Mock private AiJobService aiJobService;
    @Mock private UserService userService;
    @Mock private ProjectSection section;
    @Mock private Project project;

    private SynthesisRequestService service;

    @BeforeEach
    void setUp() {
        AiProperties aiProperties = new AiProperties(
                "none",
                new ModelOptions(
                        "model-x",
                        Duration.ofSeconds(60),
                        100_000,
                        4096,
                        131_072,
                        8_192,
                        ModelOptions.CONSERVATIVE_CHAR_V1,
                        ModelOptions.REJECT_OVERSIZED_INPUT_V1,
                        2,
                        Duration.ofMillis(500), Duration.ofSeconds(8)),
                Map.of(),
                null);
        service = new SynthesisRequestService(sectionAccessGuard, snapshotAssembler, inputHasher,
                aiJobService, aiProperties, userService);

        given(sectionAccessGuard.requireOwnedSection(SECTION_ID, USER_ID)).willReturn(section);
        given(snapshotAssembler.assemble(SECTION_ID))
                .willReturn(new SynthesisInputSnapshot(List.of(), List.of(), 0));
        given(inputHasher.hash(any())).willReturn(HASH);
        given(section.getId()).willReturn(SECTION_ID);
        given(section.getProject()).willReturn(project);
        given(project.getId()).willReturn(PROJECT_ID);
    }

    private AiJobCreateResult result(UUID requestId, AiJobStatus status, boolean created) {
        return new AiJobCreateResult(requestId, status, null, 1, created, false);
    }

    @Test
    @DisplayName("재사용 가능한 성공 작업이 있으면 상태 검증 없이 그 requestId를 반환한다")
    void reuseSucceededJob_skipsStatusCheck() {
        UUID existing = UUID.randomUUID();
        given(section.getStatus()).willReturn(ProjectSectionStatus.DRAFTING); // SYNTHESIZING이 아니어도 재사용
        given(aiJobService.findLatest(any()))
                .willReturn(Optional.of(result(existing, AiJobStatus.SUCCEEDED, false)));

        UUID requestId = service.requestSynthesis(SECTION_ID, USER_ID);

        assertThat(requestId).isEqualTo(existing);
        verify(aiJobService, never()).createOrGet(any());
        verify(aiJobService, never()).retry(any(), any());
    }

    @Test
    @DisplayName("진행 중(RUNNING) 작업도 재사용한다")
    void reuseRunningJob() {
        UUID existing = UUID.randomUUID();
        given(aiJobService.findLatest(any()))
                .willReturn(Optional.of(result(existing, AiJobStatus.RUNNING, false)));

        assertThat(service.requestSynthesis(SECTION_ID, USER_ID)).isEqualTo(existing);
        verify(aiJobService, never()).createOrGet(any());
    }

    @Test
    @DisplayName("기존 작업이 없고 SYNTHESIZING이면 새 작업을 만든다")
    void createsNewJob() {
        UUID created = UUID.randomUUID();
        given(section.getStatus()).willReturn(ProjectSectionStatus.SYNTHESIZING);
        given(aiJobService.findLatest(any())).willReturn(Optional.empty());
        given(userService.getUserReference(USER_ID)).willReturn(org.mockito.Mockito.mock(User.class));
        given(aiJobService.createOrGet(any())).willReturn(result(created, AiJobStatus.QUEUED, true));

        UUID requestId = service.requestSynthesis(SECTION_ID, USER_ID);

        assertThat(requestId).isEqualTo(created);
        verify(aiJobService).createOrGet(any());
        verify(aiJobService, never()).retry(any(), any());
    }

    @Test
    @DisplayName("새 작업을 만들어야 하는데 SYNTHESIZING이 아니면 S002를 던진다")
    void newJobButNotSynthesizing_throwsS002() {
        given(section.getStatus()).willReturn(ProjectSectionStatus.COLLECTING);
        given(aiJobService.findLatest(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.requestSynthesis(SECTION_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_SECTION_STATUS_TRANSITION));

        verify(aiJobService, never()).createOrGet(any());
        verify(aiJobService, never()).retry(any(), any());
    }

    @Test
    @DisplayName("기존 작업이 실패 상태면 재시도(retry)로 새 작업을 만든다")
    void failedJob_isRetried() {
        UUID failed = UUID.randomUUID();
        UUID retried = UUID.randomUUID();
        given(section.getStatus()).willReturn(ProjectSectionStatus.SYNTHESIZING);
        given(aiJobService.findLatest(any()))
                .willReturn(Optional.of(result(failed, AiJobStatus.FAILED, false)));
        given(userService.getUserReference(USER_ID)).willReturn(org.mockito.Mockito.mock(User.class));
        given(aiJobService.retry(eq(failed), any())).willReturn(result(retried, AiJobStatus.QUEUED, true));

        assertThat(service.requestSynthesis(SECTION_ID, USER_ID)).isEqualTo(retried);
        verify(aiJobService).retry(eq(failed), any());
        verify(aiJobService, never()).createOrGet(any());
    }
}
