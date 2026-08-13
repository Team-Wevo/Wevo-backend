package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredOutputSemanticException;
import com.wevo.backend.ai.context.AiOpinionContext;
import com.wevo.backend.ai.context.ContextChunk;
import com.wevo.backend.ai.context.ContextChunkPlan;
import com.wevo.backend.ai.dto.model.IssueDetectionIssueOutput;
import com.wevo.backend.ai.dto.model.IssueDetectionOutput;
import com.wevo.backend.issue.domain.IssueType;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IssueDetectionResultMergerTest {

    private final IssueDetectionResultMerger merger =
            new IssueDetectionResultMerger(new IssueDetectionOutputValidator());

    @Test
    void validatesFinalAiMergeAndKeepsPlanCoverage() {
        ContextChunkPlan plan = plan();
        IssueDetectionIssueOutput crossChunk = conflict("chunk 간 충돌", List.of(2L, 3L));

        IssueDetectionResult result = merger.merge(
                plan,
                new IssueDetectionOutput(List.of(crossChunk))
        );

        assertThat(result.issues()).containsExactly(crossChunk);
        assertThat(result.eligibleOpinionIds()).containsExactly(1L, 2L, 3L);
        assertThat(result.coveredOpinionIds()).containsExactly(1L, 2L, 3L);
    }

    @Test
    void rejectsFinalEvidenceOutsideGlobalInputAndGlobalIssueLimit() {
        ContextChunkPlan plan = plan();
        assertThatThrownBy(() -> merger.merge(
                plan,
                new IssueDetectionOutput(List.of(conflict("환각", List.of(4L))))
        )).isInstanceOf(StructuredOutputSemanticException.class);

        assertThatThrownBy(() -> merger.merge(
                plan,
                new IssueDetectionOutput(List.of(
                        conflict("충돌 1", List.of(1L)),
                        conflict("충돌 2", List.of(2L)),
                        conflict("충돌 3", List.of(3L)),
                        gap("공백 1", List.of(3L)),
                        gap("공백 2", List.of(3L))))
        )).isInstanceOf(StructuredOutputSemanticException.class);
    }

    @Test
    void requiresPlanAndFinalOutput() {
        assertThatThrownBy(() -> merger.merge(plan(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ContextChunkPlan plan() {
        AiOpinionContext first = opinion(1L, "2026-07-25T10:00:00");
        AiOpinionContext second = opinion(2L, "2026-07-25T10:01:00");
        AiOpinionContext third = opinion(3L, "2026-07-25T10:02:00");
        return new ContextChunkPlan(
                List.of(
                        new ContextChunk(1, List.of(first, second), List.of(1L, 2L), 100),
                        new ContextChunk(2, List.of(third), List.of(3L), 80)
                ),
                List.of(1L, 2L, 3L),
                List.of(1L, 2L, 3L),
                true
        );
    }

    private AiOpinionContext opinion(Long id, String submittedAt) {
        return new AiOpinionContext(id, "member-" + id, "의견 " + id, submittedAt);
    }

    private IssueDetectionIssueOutput conflict(String description, List<Long> evidence) {
        return new IssueDetectionIssueOutput(
                IssueType.CONFLICT,
                description,
                evidence,
                "질문",
                List.of("A", "B")
        );
    }

    private IssueDetectionIssueOutput gap(String description, List<Long> evidence) {
        return new IssueDetectionIssueOutput(
                IssueType.GAP,
                description,
                evidence,
                null,
                List.of()
        );
    }
}
