package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredOutputSemanticException;
import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.dto.model.IssueDetectionIssueOutput;
import com.wevo.backend.ai.dto.model.IssueDetectionOutput;
import com.wevo.backend.issue.domain.IssueType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IssueDetectionOutputValidatorTest {

    private final IssueDetectionOutputValidator validator = new IssueDetectionOutputValidator();
    private final StructuredOutputValidationContext context =
            new StructuredOutputValidationContext(Set.of(1L, 2L, 3L, 4L, 5L));

    @Test
    void acceptsConflictGapMixedAndNoIssueResults() {
        assertThatCode(() -> validator.validate(new IssueDetectionOutput(List.of()), context))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(
                new IssueDetectionOutput(List.of(
                        conflict("우선순위가 충돌합니다.", List.of(1L, 2L)),
                        gap("예산 근거와 견적 자료가 필요합니다.", List.of(3L))
                )),
                context
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingDuplicateAndUnknownEvidence() {
        assertInvalid(conflict("충돌", List.of()));
        assertInvalid(conflict("충돌", List.of(1L, 1L)));
        assertInvalid(conflict("충돌", List.of(99L)));
    }

    @Test
    void rejectsTypeSpecificForbiddenAndMissingFields() {
        assertInvalid(new IssueDetectionIssueOutput(
                IssueType.CONFLICT, "충돌", List.of(1L), null, List.of("A", "B")));
        assertInvalid(new IssueDetectionIssueOutput(
                IssueType.CONFLICT, "충돌", List.of(1L), "질문", List.of("A")));
        assertInvalid(new IssueDetectionIssueOutput(
                IssueType.CONFLICT, "충돌", List.of(1L), "질문", List.of("A", " A ")));
        assertInvalid(new IssueDetectionIssueOutput(
                IssueType.GAP, "공백", List.of(1L), "금지 질문", List.of()));
        assertInvalid(new IssueDetectionIssueOutput(
                IssueType.GAP, "공백", List.of(1L), null, List.of("금지 선택지")));
    }

    @Test
    void rejectsPerTypeTotalAndStringLimits() {
        IssueDetectionIssueOutput conflict = conflict("충돌", List.of(1L));
        IssueDetectionIssueOutput gap = gap("공백", List.of(2L));

        assertInvalid(new IssueDetectionOutput(List.of(
                conflict, conflict, conflict, conflict
        )));
        assertInvalid(new IssueDetectionOutput(List.of(gap, gap, gap)));
        assertInvalid(new IssueDetectionOutput(List.of(
                conflict, conflict, conflict, gap, gap
        )));
        assertInvalid(new IssueDetectionIssueOutput(
                IssueType.GAP,
                "x".repeat(IssueDetectionOutputValidator.MAX_DESCRIPTION_LENGTH + 1),
                List.of(1L),
                null,
                List.of()
        ));
        assertInvalid(new IssueDetectionIssueOutput(
                IssueType.CONFLICT,
                "충돌",
                List.of(1L),
                "질문",
                List.of("A", "x".repeat(IssueDetectionOutputValidator.MAX_OPTION_LENGTH + 1))
        ));
    }

    private IssueDetectionIssueOutput conflict(String description, List<Long> evidence) {
        return new IssueDetectionIssueOutput(
                IssueType.CONFLICT,
                description,
                evidence,
                "어느 방향으로 결정할까요?",
                List.of("방향 A", "방향 B")
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

    private void assertInvalid(IssueDetectionIssueOutput issue) {
        assertInvalid(new IssueDetectionOutput(List.of(issue)));
    }

    private void assertInvalid(IssueDetectionOutput output) {
        assertThatThrownBy(() -> validator.validate(output, context))
                .isInstanceOf(StructuredOutputSemanticException.class)
                .hasMessageNotContaining("충돌")
                .hasMessageNotContaining("공백");
    }
}
