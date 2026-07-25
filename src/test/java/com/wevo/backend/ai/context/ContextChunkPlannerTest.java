package com.wevo.backend.ai.context;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.domain.AiFeature;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContextChunkPlannerTest {

    @Test
    void createsStableOpinionBoundariesAndExactCoverage() {
        AiProperties properties = AiTokenBudgetEstimatorTest.properties(22);
        ContextChunkPlanner planner = new ContextChunkPlanner(
                properties,
                new AiTokenBudgetEstimator(properties)
        );
        List<AiOpinionContext> reverseOrder = List.of(
                opinion(3L, "cccccccc", "2026-07-24T10:03:00"),
                opinion(1L, "aaaaaaaa", "2026-07-24T10:01:00"),
                opinion(2L, "bbbbbbbb", "2026-07-24T10:02:00")
        );

        ContextChunkPlan plan = planner.plan(
                AiFeature.OPINION_SYNTHESIS,
                reverseOrder,
                opinions -> AiTokenBudgetInput.of(
                        "fixed",
                        opinions.stream().map(AiOpinionContext::submittedContent)
                                .collect(java.util.stream.Collectors.joining())
                )
        );

        assertThat(plan.chunks()).hasSize(2);
        assertThat(plan.chunks().get(0).opinionIds()).containsExactly(1L, 2L);
        assertThat(plan.chunks().get(1).opinionIds()).containsExactly(3L);
        assertThat(plan.eligibleOpinionIds()).containsExactly(1L, 2L, 3L);
        assertThat(plan.coveredOpinionIds()).containsExactly(1L, 2L, 3L);
        assertThat(plan.complete()).isTrue();
    }

    @Test
    void rejectsSingleOversizedOpinionWithoutCharacterTruncation() {
        AiProperties properties = AiTokenBudgetEstimatorTest.properties(10);
        ContextChunkPlanner planner = new ContextChunkPlanner(
                properties,
                new AiTokenBudgetEstimator(properties)
        );
        AiOpinionContext oversized = opinion(1L, "not-to-be-truncated", "2026-07-24T10:01:00");

        assertThatThrownBy(() -> planner.plan(
                AiFeature.OPINION_SYNTHESIS,
                List.of(oversized),
                opinions -> AiTokenBudgetInput.of(
                        "fixed",
                        opinions.stream().map(AiOpinionContext::submittedContent)
                                .collect(java.util.stream.Collectors.joining())
                )
        ))
                .isInstanceOf(AiInputBudgetExceededException.class)
                .hasMessageNotContaining("not-to-be-truncated");
    }

    @Test
    void refusesIncompleteOrDuplicateCoverageAsSuccessfulPlan() {
        ContextChunk chunk = new ContextChunk(
                1,
                List.of(opinion(1L, "a", "2026-07-24T10:01:00")),
                List.of(1L),
                1
        );

        assertThatThrownBy(() -> new ContextChunkPlan(
                List.of(chunk),
                List.of(1L, 2L),
                List.of(1L),
                true
        )).isInstanceOf(IncompleteContextCoverageException.class);
    }

    private AiOpinionContext opinion(long id, String content, String submittedAt) {
        return new AiOpinionContext(id, "member-" + id, content, submittedAt);
    }
}
