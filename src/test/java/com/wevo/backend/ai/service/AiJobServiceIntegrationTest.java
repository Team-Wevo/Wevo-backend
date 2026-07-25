package com.wevo.backend.ai.service;

import com.wevo.backend.ai.domain.AiErrorType;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.domain.AiUsageLog;
import com.wevo.backend.ai.exception.AiProviderException;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.ai.repository.AiUsageLogRepository;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.project.repository.ProjectRepository;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import com.wevo.backend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Import(PostgresTestContainerConfig.class)
class AiJobServiceIntegrationTest {

    private static final String SNAPSHOT = "a".repeat(64);
    private static final String CHANGED_SNAPSHOT = "b".repeat(64);

    @Autowired
    private AiJobService jobService;

    @Autowired
    private AiJobRepository jobRepository;

    @Autowired
    private AiUsageService usageService;

    @Autowired
    private AiUsageLogRepository usageLogRepository;

    @Autowired
    private ProjectSectionRepository sectionRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    private User user;
    private Project project;
    private ProjectSection section;

    @AfterEach
    void tearDown() {
        usageLogRepository.deleteAll();
        jobRepository.deleteAll();
        sectionRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    @BeforeEach
    void setUp() {
        usageLogRepository.deleteAll();
        jobRepository.deleteAll();
        sectionRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();

        user = userRepository.save(User.builder()
                .name("AI tester")
                .email("ai-job-tester@example.com")
                .status(UserStatus.ACTIVE)
                .build());
        project = projectRepository.save(Project.builder()
                .owner(user)
                .title("AI project")
                .resultType(OutputType.PROPOSAL)
                .audience("AI test audience")
                .status(ProjectStatus.ACTIVE)
                .build());
        section = sectionRepository.save(ProjectSection.builder()
                .project(project)
                .title("Problem")
                .sectionOrder(1)
                .status(ProjectSectionStatus.SYNTHESIZING)
                .build());
    }

    @Test
    void sequentialSameKeyCreatesOneJobAndReusesSuccessfulResult() {
        AiJobCreateResult created = jobService.createOrGet(command());
        AiJobCreateResult duplicate = jobService.createOrGet(command());

        assertThat(created.created()).isTrue();
        assertThat(duplicate.created()).isFalse();
        assertThat(duplicate.requestId()).isEqualTo(created.requestId());
        assertThat(jobRepository.count()).isEqualTo(1);

        jobService.start(created.requestId(), SNAPSHOT);
        jobService.succeed(created.requestId(), () -> SNAPSHOT, () -> 91L);

        AiJobCreateResult reused = jobService.createOrGet(command());
        assertThat(reused.resultReused()).isTrue();
        assertThat(reused.resultId()).isEqualTo(91L);
        assertThat(reused.requestId()).isEqualTo(created.requestId());
    }

    @Test
    void concurrentSameKeyCreatesExactlyOneJob() throws Exception {
        AiJobCreateCommand command = command();

        List<AiJobCreateResult> results = runConcurrently(6, () -> jobService.createOrGet(command));

        assertThat(results).extracting(AiJobCreateResult::requestId).containsOnly(results.getFirst().requestId());
        assertThat(results).filteredOn(AiJobCreateResult::created).hasSize(1);
        assertThat(jobRepository.count()).isEqualTo(1);
    }

    @Test
    void concurrentWorkersClaimOnlyOnce() throws Exception {
        UUID requestId = jobService.createOrGet(command()).requestId();

        List<AiJobStartResult> results = runConcurrently(2, () -> jobService.start(requestId, SNAPSHOT));

        assertThat(results).filteredOn(AiJobStartResult::claimed).hasSize(1);
        assertThat(results).extracting(AiJobStartResult::status).containsOnly(AiJobStatus.RUNNING);
        AiJob running = jobRepository.findByRequestId(requestId).orElseThrow();
        assertThat(running.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void staleBeforeStartSkipsClaimAndStaleBeforeCompletionSkipsWriter() {
        UUID beforeStart = jobService.createOrGet(command()).requestId();
        AiJobStartResult skipped = jobService.start(beforeStart, CHANGED_SNAPSHOT);

        assertThat(skipped.claimed()).isFalse();
        assertThat(skipped.status()).isEqualTo(AiJobStatus.STALE);

        UUID beforeCompletion = jobService.createOrGet(command(CHANGED_SNAPSHOT)).requestId();
        jobService.start(beforeCompletion, CHANGED_SNAPSHOT);
        AtomicBoolean writerCalled = new AtomicBoolean();

        AiJobCompletionResult stale = jobService.succeed(beforeCompletion, () -> SNAPSHOT, () -> {
            writerCalled.set(true);
            return 92L;
        });

        assertThat(stale.status()).isEqualTo(AiJobStatus.STALE);
        assertThat(stale.resultPersisted()).isFalse();
        assertThat(writerCalled).isFalse();
        assertThat(jobRepository.findByRequestId(beforeCompletion).orElseThrow().getResultId()).isNull();
    }

    @Test
    void completionFromQueuedStateRejectsBeforeCallingWriter() {
        UUID requestId = jobService.createOrGet(command()).requestId();
        AtomicBoolean writerCalled = new AtomicBoolean();

        assertThatThrownBy(() -> jobService.succeed(requestId, () -> SNAPSHOT, () -> {
            writerCalled.set(true);
            return 1L;
        })).isInstanceOf(BusinessException.class);

        assertThat(writerCalled).isFalse();
        assertThat(jobRepository.findByRequestId(requestId).orElseThrow().getStatus())
                .isEqualTo(AiJobStatus.QUEUED);
    }

    @Test
    void explicitRetryCreatesOneFollowUpAndHonorsExecutionLimit() throws Exception {
        UUID first = jobService.createOrGet(command()).requestId();
        jobService.start(first, SNAPSHOT);
        jobService.fail(first, new IllegalStateException("first failed"));

        List<AiJobCreateResult> concurrentRetries = runConcurrently(2, () -> jobService.retry(first, user));
        assertThat(concurrentRetries).extracting(AiJobCreateResult::requestId)
                .containsOnly(concurrentRetries.getFirst().requestId());
        assertThat(jobRepository.count()).isEqualTo(2);

        UUID second = concurrentRetries.getFirst().requestId();
        jobService.start(second, SNAPSHOT);
        jobService.fail(second, new IllegalStateException("second failed"));
        AiJobCreateResult third = jobService.retry(second, user);
        assertThat(third.executionSequence()).isEqualTo(3);
        jobService.start(third.requestId(), SNAPSHOT);
        jobService.fail(third.requestId(), new IllegalStateException("third failed"));

        assertThatThrownBy(() -> jobService.retry(first, user))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_JOB_RETRY_LIMIT_EXCEEDED);
    }

    @Test
    void retryReturnsActiveJobAndRejectsSuccessfulJob() {
        UUID active = jobService.createOrGet(command()).requestId();

        AiJobCreateResult activeRetry = jobService.retry(active, user);
        assertThat(activeRetry.created()).isFalse();
        assertThat(activeRetry.requestId()).isEqualTo(active);

        jobService.start(active, SNAPSHOT);
        jobService.succeed(active, () -> SNAPSHOT, () -> 10L);
        assertThatThrownBy(() -> jobService.retry(active, user))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_JOB_INVALID_STATE_TRANSITION);
    }

    @Test
    void normalRequestDoesNotAutomaticallyRetryTerminalJobsAndCancelledOrStaleJobsAreRetryable() {
        AiJobCreateResult cancelled = jobService.createOrGet(command());
        jobService.cancel(cancelled.requestId());

        AiJobCreateResult cancelledDuplicate = jobService.createOrGet(command());
        assertThat(cancelledDuplicate.requestId()).isEqualTo(cancelled.requestId());
        assertThat(cancelledDuplicate.status()).isEqualTo(AiJobStatus.CANCELLED);
        assertThat(cancelledDuplicate.created()).isFalse();

        AiJobCreateResult cancelledRetry = jobService.retry(cancelled.requestId(), user);
        assertThat(cancelledRetry.created()).isTrue();
        assertThat(cancelledRetry.executionSequence()).isEqualTo(2);

        AiJobCreateResult stale = jobService.createOrGet(command(CHANGED_SNAPSHOT));
        jobService.markStale(stale.requestId());
        AiJobCreateResult staleRetry = jobService.retry(stale.requestId(), user);
        assertThat(staleRetry.created()).isTrue();
        assertThat(staleRetry.executionSequence()).isEqualTo(2);
    }

    @Test
    void queueAndTimeoutQueriesUseSeparateStartedAndHeartbeatCriteria() {
        UUID queued = jobService.createOrGet(command()).requestId();
        UUID running = jobService.createOrGet(command(CHANGED_SNAPSHOT)).requestId();
        jobService.start(running, CHANGED_SNAPSHOT);

        assertThat(jobService.findNextQueuedRequestIds(10)).contains(queued).doesNotContain(running);

        LocalDateTime futureThreshold = LocalDateTime.now().plusMinutes(1);
        assertThat(jobService.findApplicationTimedOutRequestIds(futureThreshold)).contains(running);
        assertThat(jobService.findHeartbeatTimedOutRequestIds(futureThreshold)).contains(running);

        jobService.markApplicationTimedOut(running);
        AiJob timedOut = jobRepository.findByRequestId(running).orElseThrow();
        assertThat(timedOut.getStatus()).isEqualTo(AiJobStatus.FAILED);
        assertThat(timedOut.getFinalErrorType()).isEqualTo(AiErrorType.APPLICATION_TIMEOUT);

        UUID heartbeatTimeout = jobService.createOrGet(command("c".repeat(64))).requestId();
        jobService.start(heartbeatTimeout, "c".repeat(64));
        boolean recovered = jobService.recoverIfHeartbeatStale(heartbeatTimeout, futureThreshold);
        assertThat(recovered).isTrue();
        assertThat(jobRepository.findByRequestId(heartbeatTimeout).orElseThrow().getFinalErrorType())
                .isEqualTo(AiErrorType.WORKER_HEARTBEAT_TIMEOUT);
    }

    @Test
    void structuredOutputFailuresAreNormalizedAndSanitized() {
        Map<ErrorCode, AiErrorType> errors = Map.of(
                ErrorCode.AI_STRUCTURED_OUTPUT_JSON_PARSE_FAILED, AiErrorType.JSON_PARSE_FAILED,
                ErrorCode.AI_STRUCTURED_OUTPUT_SCHEMA_VALIDATION_FAILED, AiErrorType.SCHEMA_VALIDATION_FAILED,
                ErrorCode.AI_STRUCTURED_OUTPUT_CONVERSION_FAILED, AiErrorType.TYPE_CONVERSION_FAILED,
                ErrorCode.AI_STRUCTURED_OUTPUT_SEMANTIC_VALIDATION_FAILED, AiErrorType.SEMANTIC_VALIDATION_FAILED
        );
        int index = 0;

        for (Map.Entry<ErrorCode, AiErrorType> entry : errors.entrySet()) {
            String snapshot = Integer.toHexString(++index).repeat(64);
            UUID requestId = jobService.createOrGet(command(snapshot)).requestId();
            jobService.start(requestId, snapshot);
            jobService.fail(requestId, new AiProviderException(
                    entry.getKey(),
                    new IllegalStateException("invalid output sk-ant-secret-value"),
                    null,
                    1
            ));

            AiJob failed = jobRepository.findByRequestId(requestId).orElseThrow();
            assertThat(failed.getStatus()).isEqualTo(AiJobStatus.FAILED);
            assertThat(failed.getFinalErrorType()).isEqualTo(entry.getValue());
            assertThat(failed.getSafeErrorMessage())
                    .isEqualTo(entry.getKey().getMessage())
                    .doesNotContain("sk-ant");
        }
    }

    @Test
    void usageLogsCanOptionallyBelongToOneJob() {
        UUID requestId = jobService.createOrGet(command()).requestId();
        AiJob job = jobRepository.findByRequestId(requestId).orElseThrow();

        usageService.startRequest(new AiUsageStartCommand(
                job, project, section, user, AiFeature.OPINION_SYNTHESIS,
                "opinion-synthesis:v1", SNAPSHOT
        ));
        usageService.startRequest(new AiUsageStartCommand(
                job, project, section, user, AiFeature.OPINION_SYNTHESIS,
                "opinion-synthesis:v1", SNAPSHOT
        ));
        usageService.startRequest(new AiUsageStartCommand(
                project, section, user, AiFeature.OPINION_SYNTHESIS,
                "opinion-synthesis:v1", SNAPSHOT
        ));

        List<AiUsageLog> logs = usageLogRepository.findAll();
        assertThat(logs).filteredOn(log -> log.getAiJob() != null).hasSize(2);
        assertThat(logs).filteredOn(log -> log.getAiJob() == null).hasSize(1);
    }

    @Test
    void linkedUsageLogRejectsMetadataThatDoesNotBelongToJob() {
        UUID requestId = jobService.createOrGet(command()).requestId();
        AiJob job = jobRepository.findByRequestId(requestId).orElseThrow();

        assertThatThrownBy(() -> new AiUsageStartCommand(
                job, project, section, user, AiFeature.DRAFT_GENERATION,
                "opinion-synthesis:v1", SNAPSHOT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void commandRejectsWrongProjectSectionAndMissingSchema() {
        Project otherProject = projectRepository.save(Project.builder()
                .owner(user)
                .title("Other")
                .resultType(OutputType.PROPOSAL)
                .audience("AI test audience")
                .status(ProjectStatus.ACTIVE)
                .build());

        assertThatThrownBy(() -> new AiJobCreateCommand(
                otherProject, section, user, AiFeature.OPINION_SYNTHESIS,
                SNAPSHOT, "opinions:v1", "prompt:v1", "schema:v1", "model", 100
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiJobCreateCommand(
                project, section, user, AiFeature.OPINION_SYNTHESIS,
                SNAPSHOT, "opinions:v1", "prompt:v1", " ", "model", 100
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private AiJobCreateCommand command() {
        return command(SNAPSHOT);
    }

    private AiJobCreateCommand command(String snapshot) {
        return new AiJobCreateCommand(
                project,
                section,
                user,
                AiFeature.OPINION_SYNTHESIS,
                snapshot,
                "opinions:v1",
                "opinion-synthesis:v1",
                "opinion-synthesis:v1",
                "test-model",
                128
        );
    }

    private <T> List<T> runConcurrently(int workers, Callable<T> task) throws Exception {
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(workers)) {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            ready.await();
            start.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        }
    }
}
