package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.AiUsageMetadata;
import com.wevo.backend.ai.client.ClaudeGateway;
import com.wevo.backend.ai.client.ClaudeRequest;
import com.wevo.backend.ai.domain.AiErrorType;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiRequestStatus;
import com.wevo.backend.ai.domain.AiUsageLog;
import com.wevo.backend.ai.exception.ClaudeProviderException;
import com.wevo.backend.ai.repository.AiUsageLogRepository;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.project.repository.ProjectRepository;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import com.wevo.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AiUsageLifecycleIntegrationTest {

    @Autowired
    private AiUsageLogRepository usageLogRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AiUsageService usageService;

    @Autowired
    private AiOrphanRecoveryService orphanRecoveryService;

    private User user;
    private Project project;

    @BeforeEach
    void setUp() {
        usageLogRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();

        user = userRepository.save(User.builder()
                .name("AI tester")
                .email("ai-tester@example.com")
                .status(UserStatus.ACTIVE)
                .build());
        project = projectRepository.save(Project.builder()
                .owner(user)
                .title("AI project")
                .resultType(OutputType.PROPOSAL)
                .status(ProjectStatus.ACTIVE)
                .build());
    }

    @Test
    void startsAndCompletesSuccessWithUsageAndResult() {
        AiUsageHandle handle = usageService.startRequest(startCommand());

        AiUsageLog requested = usageLogRepository.findByRequestId(handle.requestId()).orElseThrow();
        assertThat(requested.getRequestStatus()).isEqualTo(AiRequestStatus.REQUESTED);
        assertThat(handle.logId()).isNotNull();

        usageService.completeSuccess(
                handle,
                new AiUsageMetadata("provider-1", "response-model", 100L, 20L, 5L, 3L),
                2,
                91L
        );

        AiUsageLog completed = usageLogRepository.findByRequestId(handle.requestId()).orElseThrow();
        assertThat(completed.getRequestStatus()).isEqualTo(AiRequestStatus.SUCCEEDED);
        assertThat(completed.getProviderRequestId()).isEqualTo("provider-1");
        assertThat(completed.getTotalInputTokens()).isEqualTo(108L);
        assertThat(completed.getAttemptCount()).isEqualTo(2);
        assertThat(completed.getResultId()).isEqualTo(91L);
        assertThat(completed.getEstimatedCost()).isNull();
        assertThat(completed.getCompletedAt()).isNotNull();
    }

    @Test
    void invocationFailureIsPersistedAndDoesNotRemainRequested() {
        ClaudeGateway failingGateway = request -> {
            throw new ClaudeProviderException(
                    ErrorCode.AI_RATE_LIMITED,
                    new IllegalStateException("provider error"),
                    null,
                    3
            );
        };
        AiInvocationService invocationService = new AiInvocationService(failingGateway, usageService);

        assertThatThrownBy(() -> invocationService.invoke(
                startCommand(),
                new ClaudeRequest(AiFeature.DRAFT_GENERATION, "system prompt", "user prompt"),
                response -> new AiProcessedResult<>(response.content(), null)
        )).isInstanceOf(ClaudeProviderException.class);

        AiUsageLog failed = usageLogRepository.findAll().getFirst();
        assertThat(failed.getRequestStatus()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(failed.getErrorType()).isEqualTo(AiErrorType.RATE_LIMITED);
        assertThat(failed.getAttemptCount()).isEqualTo(3);
        assertThat(failed.getErrorMessage()).doesNotContain("provider error");
    }

    @Test
    void recoversOnlyRequestsOlderThanFeatureTimeoutAndGrace() {
        LocalDateTime now = LocalDateTime.now();
        AiUsageLog orphan = usageLogRepository.save(AiUsageLog.start(
                UUID.randomUUID(), project, null, user, AiFeature.DRAFT_GENERATION,
                "model", "v1", "old", now.minusSeconds(40)
        ));
        AiUsageLog recent = usageLogRepository.save(AiUsageLog.start(
                UUID.randomUUID(), project, null, user, AiFeature.DRAFT_GENERATION,
                "model", "v1", "recent", now.minusSeconds(10)
        ));

        int recovered = orphanRecoveryService.recoverOrphans();

        assertThat(recovered).isEqualTo(1);
        AiUsageLog recoveredLog = usageLogRepository.findById(orphan.getId()).orElseThrow();
        AiUsageLog untouchedLog = usageLogRepository.findById(recent.getId()).orElseThrow();
        assertThat(recoveredLog.getRequestStatus()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(recoveredLog.getErrorType()).isEqualTo(AiErrorType.ORPHANED_REQUEST);
        assertThat(recoveredLog.getAttemptCount()).isNull();
        assertThat(untouchedLog.getRequestStatus()).isEqualTo(AiRequestStatus.REQUESTED);
    }

    private AiUsageStartCommand startCommand() {
        return new AiUsageStartCommand(
                project,
                null,
                user,
                AiFeature.DRAFT_GENERATION,
                "v1",
                "snapshot-hash"
        );
    }
}
