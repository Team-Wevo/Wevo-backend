package com.wevo.backend.ai.domain;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiJobTest {

    private static final String SNAPSHOT = "a".repeat(64);
    private static final String KEY = "b".repeat(64);
    private static final LocalDateTime QUEUED_AT = LocalDateTime.of(2026, 7, 14, 10, 0);

    @Test
    void queuedJobStartsAndSucceedsWithRequiredFields() {
        AiJob job = job();
        LocalDateTime startedAt = QUEUED_AT.plusSeconds(1);
        LocalDateTime heartbeatAt = startedAt.plusSeconds(2);
        LocalDateTime completedAt = heartbeatAt.plusSeconds(3);

        job.start(startedAt);
        job.heartbeat(heartbeatAt);
        job.succeed(91L, completedAt);

        assertThat(job.getStatus()).isEqualTo(AiJobStatus.SUCCEEDED);
        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(job.getStartedAt()).isEqualTo(startedAt);
        assertThat(job.getLastHeartbeatAt()).isEqualTo(heartbeatAt);
        assertThat(job.getCompletedAt()).isEqualTo(completedAt);
        assertThat(job.getResultId()).isEqualTo(91L);
    }

    @Test
    void failureRequiresRunningStateAndNormalizedError() {
        AiJob job = job();
        job.start(QUEUED_AT.plusSeconds(1));
        LocalDateTime failedAt = QUEUED_AT.plusSeconds(2);

        job.fail(AiErrorType.SCHEMA_VALIDATION_FAILED, "safe", failedAt);

        assertThat(job.getStatus()).isEqualTo(AiJobStatus.FAILED);
        assertThat(job.getFinalErrorType()).isEqualTo(AiErrorType.SCHEMA_VALIDATION_FAILED);
        assertThat(job.getSafeErrorMessage()).isEqualTo("safe");
        assertThat(job.getFailedAt()).isEqualTo(failedAt);
        assertThat(job.getCompletedAt()).isEqualTo(failedAt);
    }

    @Test
    void queuedOrRunningJobCanBeCancelledOrMarkedStale() {
        AiJob queued = job();
        AiJob running = job();
        running.start(QUEUED_AT.plusSeconds(1));

        queued.cancel(QUEUED_AT.plusSeconds(2));
        running.markStale(QUEUED_AT.plusSeconds(3));

        assertThat(queued.getStatus()).isEqualTo(AiJobStatus.CANCELLED);
        assertThat(queued.getCancelledAt()).isEqualTo(QUEUED_AT.plusSeconds(2));
        assertThat(running.getStatus()).isEqualTo(AiJobStatus.STALE);
        assertThat(running.getFinalErrorType()).isEqualTo(AiErrorType.STALE_INPUT);
    }

    @Test
    void terminalStateCannotTransitionAgain() {
        AiJob job = job();
        job.start(QUEUED_AT.plusSeconds(1));
        job.succeed(1L, QUEUED_AT.plusSeconds(2));

        assertThatThrownBy(() -> job.fail(
                AiErrorType.INTERNAL_ERROR, "safe", QUEUED_AT.plusSeconds(3)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_JOB_INVALID_STATE_TRANSITION);
        assertThatThrownBy(() -> job.heartbeat(QUEUED_AT.plusSeconds(3)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void successAndFailureRejectMissingRequiredCompletionValues() {
        AiJob success = job();
        success.start(QUEUED_AT.plusSeconds(1));
        AiJob failure = job();
        failure.start(QUEUED_AT.plusSeconds(1));

        assertThatThrownBy(() -> success.succeed(null, QUEUED_AT.plusSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> failure.fail(null, "safe", QUEUED_AT.plusSeconds(2)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> failure.fail(
                AiErrorType.INTERNAL_ERROR, " ", QUEUED_AT.plusSeconds(2)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retryCopiesOutputIdentityAndIncrementsSequence() {
        AiJob previous = job();
        previous.start(QUEUED_AT.plusSeconds(1));
        previous.fail(AiErrorType.INTERNAL_ERROR, "safe", QUEUED_AT.plusSeconds(2));
        User retryRequester = user("retry@example.com");

        AiJob retry = AiJob.retry(
                UUID.randomUUID(), previous, retryRequester, QUEUED_AT.plusSeconds(3)
        );

        assertThat(retry.getStatus()).isEqualTo(AiJobStatus.QUEUED);
        assertThat(retry.getExecutionSequence()).isEqualTo(2);
        assertThat(retry.getRetryOf()).isSameAs(previous);
        assertThat(retry.getIdempotencyKey()).isEqualTo(previous.getIdempotencyKey());
        assertThat(retry.getInputSnapshotHash()).isEqualTo(previous.getInputSnapshotHash());
        assertThat(retry.getRolloutId()).isEqualTo(previous.getRolloutId());
        assertThat(retry.getReasoningEffort()).isEqualTo(previous.getReasoningEffort());
        assertThat(retry.getPricingVersion()).isEqualTo(previous.getPricingVersion());
        assertThat(retry.getPolicyVersion()).isEqualTo(previous.getPolicyVersion());
        assertThat(retry.getModelId()).isEqualTo(previous.getModelId());
        assertThat(retry.getPromptVersion()).isEqualTo(previous.getPromptVersion());
        assertThat(retry.getRequestedBy()).isSameAs(retryRequester);
    }

    @Test
    void creationRejectsMalformedHashAndMissingSchema() {
        Project project = project();
        User user = project.getOwner();

        assertThatThrownBy(() -> AiJob.queue(
                UUID.randomUUID(), project, null, user, AiFeature.DRAFT_GENERATION,
                "invalid", "source:v1", "prompt:v1", "schema:v1", "model", 100,
                KEY, QUEUED_AT
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AiJob.queue(
                UUID.randomUUID(), project, null, user, AiFeature.DRAFT_GENERATION,
                SNAPSHOT, "source:v1", "prompt:v1", " ", "model", 100,
                KEY, QUEUED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void jobModelDoesNotContainSensitivePayloadFields() {
        Set<String> fieldNames = Arrays.stream(AiJob.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .collect(Collectors.toSet());

        assertThat(fieldNames).doesNotContain(
                "apiKey", "systemPrompt", "userPrompt", "completion", "opinionContent"
        );
    }

    private AiJob job() {
        Project project = project();
        return AiJob.queue(
                UUID.randomUUID(), project, null, project.getOwner(), AiFeature.DRAFT_GENERATION,
                SNAPSHOT, "source:v1", "prompt:v1", "schema:v1", "model", 100,
                "candidate-5pct", "medium", "pricing:v1", "policy:v1",
                KEY, QUEUED_AT
        );
    }

    private Project project() {
        User owner = user("owner@example.com");
        return Project.builder()
                .owner(owner)
                .title("AI project")
                .resultType(OutputType.PROPOSAL)
                .status(ProjectStatus.ACTIVE)
                .build();
    }

    private User user(String email) {
        return User.builder()
                .name("AI tester")
                .email(email)
                .status(UserStatus.ACTIVE)
                .build();
    }
}
