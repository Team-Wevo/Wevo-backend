package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.AiUsageMetadata;
import com.wevo.backend.ai.client.AiProviderGateway;
import com.wevo.backend.ai.client.AiProviderRequest;
import com.wevo.backend.ai.client.AiProviderResponse;
import com.wevo.backend.ai.client.OutputSchemaId;
import com.wevo.backend.ai.client.StructuredAiProviderRequest;
import com.wevo.backend.ai.client.StructuredAiProviderResponse;
import com.wevo.backend.ai.client.StructuredOutputDefinition;
import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.domain.AiErrorType;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiRequestStatus;
import com.wevo.backend.ai.domain.AiUsageLog;
import com.wevo.backend.ai.exception.AiProviderException;
import com.wevo.backend.ai.repository.AiUsageLogRepository;
import com.wevo.backend.ai.prompt.PromptTemplateId;
import com.wevo.backend.ai.prompt.RenderedPrompt;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
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
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Import(PostgresTestContainerConfig.class)
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
                .audience("AI test audience")
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
                new AiUsageMetadata(
                        "openai", "provider-1", "response-model", 100L, 20L, 5L, 3L, 8L
                ),
                2,
                91L
        );

        AiUsageLog completed = usageLogRepository.findByRequestId(handle.requestId()).orElseThrow();
        assertThat(completed.getRequestStatus()).isEqualTo(AiRequestStatus.SUCCEEDED);
        assertThat(completed.getProvider()).isEqualTo("openai");
        assertThat(completed.getProviderRequestId()).isEqualTo("provider-1");
        assertThat(completed.getTotalInputTokens()).isEqualTo(108L);
        assertThat(completed.getReasoningTokens()).isEqualTo(8L);
        assertThat(completed.getAttemptCount()).isEqualTo(2);
        assertThat(completed.getResultId()).isEqualTo(91L);
        assertThat(completed.getEstimatedCost()).isNull();
        assertThat(completed.getCompletedAt()).isNotNull();
    }

    @Test
    void invocationFailureIsPersistedAndDoesNotRemainRequested() {
        AiProviderGateway failingGateway = request -> {
            throw new AiProviderException(
                    ErrorCode.AI_RATE_LIMITED,
                    new IllegalStateException("provider error"),
                    null,
                    3
            );
        };
        AiInvocationService invocationService = new AiInvocationService(failingGateway, usageService);

        assertThatThrownBy(() -> invocationService.invoke(
                startCommand(),
                new AiProviderRequest(AiFeature.DRAFT_GENERATION, "system prompt", "user prompt"),
                response -> new AiProcessedResult<>(response.content(), null)
        )).isInstanceOf(AiProviderException.class);

        AiUsageLog failed = usageLogRepository.findAll().getFirst();
        assertThat(failed.getRequestStatus()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(failed.getErrorType()).isEqualTo(AiErrorType.RATE_LIMITED);
        assertThat(failed.getAttemptCount()).isEqualTo(3);
        assertThat(failed.getErrorMessage()).doesNotContain("provider error");
    }

    @Test
    void resultProcessingFailurePreservesSuccessfulProviderUsage() {
        AiUsageMetadata usage = new AiUsageMetadata(
                "provider-success", "response-model", 120L, 30L, 7L, 2L
        );
        AiInvocationService invocationService = new AiInvocationService(
                successfulGateway(usage, 2), usageService
        );

        assertThatThrownBy(() -> invocationService.invoke(
                startCommand(),
                new AiProviderRequest(AiFeature.DRAFT_GENERATION, "system prompt", "user prompt"),
                response -> {
                    throw new IllegalStateException("result persistence failed");
                }
        )).isInstanceOf(IllegalStateException.class);

        AiUsageLog failed = usageLogRepository.findAll().getFirst();
        assertThat(failed.getRequestStatus()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(failed.getErrorType()).isEqualTo(AiErrorType.INTERNAL_ERROR);
        assertThat(failed.getProviderRequestId()).isEqualTo("provider-success");
        assertThat(failed.getModelId()).isEqualTo("response-model");
        assertThat(failed.getInputTokens()).isEqualTo(120L);
        assertThat(failed.getOutputTokens()).isEqualTo(30L);
        assertThat(failed.getTotalInputTokens()).isEqualTo(129L);
        assertThat(failed.getAttemptCount()).isEqualTo(2);
    }

    @Test
    void nullResultAlsoPreservesSuccessfulProviderUsage() {
        AiUsageMetadata usage = new AiUsageMetadata(
                "provider-null-result", "response-model", 40L, 10L, null, null
        );
        AiInvocationService invocationService = new AiInvocationService(
                successfulGateway(usage, 1), usageService
        );

        assertThatThrownBy(() -> invocationService.invoke(
                startCommand(),
                new AiProviderRequest(AiFeature.DRAFT_GENERATION, "system prompt", "user prompt"),
                response -> null
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AI 결과 처리 결과는 null일 수 없습니다.");

        AiUsageLog failed = usageLogRepository.findAll().getFirst();
        assertThat(failed.getRequestStatus()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(failed.getProviderRequestId()).isEqualTo("provider-null-result");
        assertThat(failed.getInputTokens()).isEqualTo(40L);
        assertThat(failed.getOutputTokens()).isEqualTo(10L);
        assertThat(failed.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void structuredValidationFailureIsAuditedBeforeResultHandlerCanPersist() {
        AiUsageMetadata usage = new AiUsageMetadata(
                "provider-structured", "response-model", 30L, 10L, null, null
        );
        AiProviderGateway failingGateway = new AiProviderGateway() {
            @Override
            public AiProviderResponse generate(AiProviderRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> StructuredAiProviderResponse<T> generateStructured(StructuredAiProviderRequest<T> request) {
                throw new AiProviderException(
                        ErrorCode.AI_STRUCTURED_OUTPUT_SCHEMA_VALIDATION_FAILED,
                        new IllegalStateException("invalid structured output"),
                        usage,
                        3
                );
            }
        };
        AiInvocationService invocationService = new AiInvocationService(failingGateway, usageService);
        AtomicBoolean handlerCalled = new AtomicBoolean();

        assertThatThrownBy(() -> invocationService.invokeStructured(
                startCommand("contract-summary:v1"),
                structuredRequest(),
                response -> {
                    handlerCalled.set(true);
                    return new AiProcessedResult<>(response.result(), 91L);
                }
        )).isInstanceOf(AiProviderException.class);

        assertThat(handlerCalled).isFalse();
        AiUsageLog failed = usageLogRepository.findAll().getFirst();
        assertThat(failed.getRequestStatus()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(failed.getErrorType()).isEqualTo(AiErrorType.SCHEMA_VALIDATION_FAILED);
        assertThat(failed.getProviderRequestId()).isEqualTo("provider-structured");
        assertThat(failed.getInputTokens()).isEqualTo(30L);
        assertThat(failed.getOutputTokens()).isEqualTo(10L);
        assertThat(failed.getAttemptCount()).isEqualTo(3);
    }

    @Test
    void recoversOnlyRequestsOlderThanFeatureTimeoutAndGrace() {
        LocalDateTime now = LocalDateTime.now();
        AiUsageLog orphan = usageLogRepository.save(AiUsageLog.start(
                UUID.randomUUID(), project, null, user, AiFeature.DRAFT_GENERATION,
                "nvidia", "model", "v1", "old", now.minusSeconds(40)
        ));
        AiUsageLog recent = usageLogRepository.save(AiUsageLog.start(
                UUID.randomUUID(), project, null, user, AiFeature.DRAFT_GENERATION,
                "nvidia", "model", "v1", "recent", now.minusSeconds(10)
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
        return startCommand("v1");
    }

    private AiUsageStartCommand startCommand(String promptVersion) {
        return new AiUsageStartCommand(
                project,
                null,
                user,
                AiFeature.DRAFT_GENERATION,
                promptVersion,
                "snapshot-hash"
        );
    }

    private StructuredAiProviderRequest<TestOutput> structuredRequest() {
        return new StructuredAiProviderRequest<>(
                AiFeature.DRAFT_GENERATION,
                new RenderedPrompt(new PromptTemplateId("contract-summary", 1), "system", "user"),
                StructuredOutputDefinition.of(new OutputSchemaId("test-output", 1), TestOutput.class),
                new StructuredOutputValidationContext(Set.of(7L))
        );
    }

    private AiProviderGateway successfulGateway(AiUsageMetadata usage, int attemptCount) {
        return request -> new AiProviderResponse("generated content", usage, "stop", attemptCount);
    }

    private record TestOutput(Long resourceId) {
    }
}
