package com.wevo.backend.ai.domain;

import com.wevo.backend.ai.client.AiUsageMetadata;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiUsageLogTest {

    private final User user = User.builder()
            .name("tester")
            .email("tester@example.com")
            .status(UserStatus.ACTIVE)
            .build();
    private final Project project = Project.builder()
            .owner(user)
            .title("project")
            .resultType(OutputType.PROPOSAL)
            .status(ProjectStatus.ACTIVE)
            .build();

    @Test
    void startsInRequestedAndCompletesSuccessfullyOnce() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 7, 13, 10, 0);
        AiUsageLog log = start(startedAt);

        assertThat(log.getRequestStatus()).isEqualTo(AiRequestStatus.REQUESTED);
        assertThat(log.getProvider()).isEqualTo("nvidia");

        AiUsageMetadata usage = new AiUsageMetadata(
                "openai", "provider-1", "model-2", 10L, 5L, 3L, 2L, 4L
        );
        AiCostSnapshot cost = new AiCostSnapshot(
                "v1",
                new BigDecimal("1.0"),
                new BigDecimal("2.0"),
                new BigDecimal("0.1"),
                new BigDecimal("1.2"),
                new BigDecimal("0.000023700000")
        );
        log.completeSuccess(usage, cost, 2, 77L, startedAt.plusSeconds(2));

        assertThat(log.getRequestStatus()).isEqualTo(AiRequestStatus.SUCCEEDED);
        assertThat(log.getProviderRequestId()).isEqualTo("provider-1");
        assertThat(log.getProvider()).isEqualTo("openai");
        assertThat(log.getModelId()).isEqualTo("model-2");
        assertThat(log.getTotalInputTokens()).isEqualTo(15L);
        assertThat(log.getReasoningTokens()).isEqualTo(4L);
        assertThat(log.getAttemptCount()).isEqualTo(2);
        assertThat(log.getLatencyMs()).isEqualTo(2_000L);
        assertThat(log.getResultId()).isEqualTo(77L);

        assertThatThrownBy(() -> log.completeFailure(
                null, null, 1, AiErrorType.INTERNAL_ERROR, "error", startedAt.plusSeconds(3)
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void completesFailureAndRejectsDuplicateCompletion() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 7, 13, 10, 0);
        AiUsageLog log = start(startedAt);

        log.completeFailure(
                null,
                AiCostSnapshot.unpriced("v2"),
                1,
                AiErrorType.RATE_LIMITED,
                "요청이 많습니다.",
                startedAt.plusSeconds(1)
        );

        assertThat(log.getRequestStatus()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(log.getErrorType()).isEqualTo(AiErrorType.RATE_LIMITED);
        assertThat(log.getEstimatedCost()).isNull();
        assertThatThrownBy(() -> log.completeSuccess(
                null, null, 1, null, startedAt.plusSeconds(2)
        )).isInstanceOf(IllegalStateException.class);
    }

    private AiUsageLog start(LocalDateTime startedAt) {
        return AiUsageLog.start(
                UUID.randomUUID(), project, null, user, AiFeature.DRAFT_GENERATION,
                "nvidia", "model-1", "v1", "abc123", startedAt
        );
    }
}
