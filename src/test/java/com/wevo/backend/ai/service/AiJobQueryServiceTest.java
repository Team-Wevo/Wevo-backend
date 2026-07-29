package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.wevo.backend.ai.domain.AiErrorType;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.domain.AiRequestFeature;
import com.wevo.backend.ai.domain.AiRequestStatus;
import com.wevo.backend.ai.dto.response.AiJobResponse;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.service.ProjectAccessGuard;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiJobQueryServiceTest {

    private static final UUID REQUEST_ID =
            UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final long PROJECT_ID = 10L;
    private static final long USER_ID = 20L;

    @Mock private AiJobRepository jobRepository;
    @Mock private ProjectAccessGuard projectAccessGuard;
    @Mock private AiJob job;
    @Mock private Project project;

    private AiJobQueryService service;

    @BeforeEach
    void setUp() {
        service = new AiJobQueryService(
                jobRepository,
                projectAccessGuard,
                new AiJobStatusMapper()
        );
        given(jobRepository.findByRequestId(REQUEST_ID)).willReturn(Optional.of(job));
        given(job.getRequestId()).willReturn(REQUEST_ID);
        given(job.getProject()).willReturn(project);
        given(project.getId()).willReturn(PROJECT_ID);
    }

    @Test
    void anyProjectMemberCanReadExternalStatusWithoutBeingRequester() {
        given(job.getFeature()).willReturn(AiFeature.OPINION_SYNTHESIS);
        given(job.getStatus()).willReturn(AiJobStatus.RUNNING);

        AiJobResponse response = service.getJob(REQUEST_ID, USER_ID);

        assertThat(response.requestId()).isEqualTo(REQUEST_ID);
        assertThat(response.feature()).isEqualTo(AiRequestFeature.SYNTHESIS);
        assertThat(response.status()).isEqualTo(AiRequestStatus.REQUESTED);
        assertThat(response.failure()).isNull();
        verify(projectAccessGuard).requireParticipant(PROJECT_ID, USER_ID);
    }

    @Test
    void mapsInternalFeaturesToOnlyThreePublicFeatureNames() {
        given(job.getStatus()).willReturn(AiJobStatus.SUCCEEDED);

        given(job.getFeature()).willReturn(AiFeature.ISSUE_DETECTION);
        assertThat(service.getJob(REQUEST_ID, USER_ID).feature())
                .isEqualTo(AiRequestFeature.SYNTHESIS);

        given(job.getFeature()).willReturn(AiFeature.DRAFT_GENERATION);
        assertThat(service.getJob(REQUEST_ID, USER_ID).feature())
                .isEqualTo(AiRequestFeature.DRAFT_GENERATION);

        given(job.getFeature()).willReturn(AiFeature.DRAFT_REVIEW);
        assertThat(service.getJob(REQUEST_ID, USER_ID).feature())
                .isEqualTo(AiRequestFeature.PRECHECK);
    }

    @Test
    void exposesStructuredOutputFailureAsCanonicalCodeOnly() {
        given(job.getFeature()).willReturn(AiFeature.DRAFT_REVIEW);
        given(job.getStatus()).willReturn(AiJobStatus.FAILED);
        given(job.getFinalErrorType())
                .willReturn(AiErrorType.SCHEMA_VALIDATION_FAILED);
        given(job.getSafeErrorMessage())
                .willReturn("AI 응답이 출력 형식을 충족하지 않습니다.");

        AiJobResponse response = service.getJob(REQUEST_ID, USER_ID);

        assertThat(response.status()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(response.failure().errorCode()).isEqualTo("AI018");
        assertThat(response.failure().message())
                .isEqualTo("AI 응답이 출력 형식을 충족하지 않습니다.");
    }

    @Test
    void missingJobReturnsAi021WithoutMembershipLookup() {
        given(jobRepository.findByRequestId(REQUEST_ID)).willReturn(Optional.empty());

        assertAi021();
        verifyNoInteractions(projectAccessGuard);
    }

    @Test
    void nonMemberIsHiddenAsSameAi021() {
        given(projectAccessGuard.requireParticipant(PROJECT_ID, USER_ID))
                .willThrow(new BusinessException(ErrorCode.NOT_PROJECT_MEMBER));

        assertAi021();
    }

    private void assertAi021() {
        assertThatThrownBy(() -> service.getJob(REQUEST_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.AI_JOB_NOT_FOUND));
    }
}
