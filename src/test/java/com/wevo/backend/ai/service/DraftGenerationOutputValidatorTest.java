package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wevo.backend.ai.client.StructuredOutputSemanticException;
import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DraftGenerationOutputValidatorTest {

    private final DraftGenerationOutputValidator validator =
            new DraftGenerationOutputValidator();
    private final StructuredOutputValidationContext context =
            new StructuredOutputValidationContext(
                    Set.of(1L, 2L),
                    Set.of(11L, 12L),
                    Set.of(21L),
                    Set.of(12L)
            );

    @Test
    void acceptsExactEvidenceAndUnresolvedGapCoverage() {
        assertThatCode(() -> validator.validate(
                output(
                        "유효한 본문 [미확인:GAP-12]",
                        List.of(2L, 1L),
                        List.of(12L, 11L),
                        List.of(21L),
                        List.of(12L)
                ),
                context
        )).doesNotThrowAnyException();
    }

    @Test
    void acceptsMissingOptionalEvidenceBecausePersistenceUsesTheTrustedContext() {
        assertThatCode(() -> validator.validate(
                output(List.of(1L), List.of(11L), List.of(), List.of(12L)),
                context
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnknownOptionalEvidence() {
        assertRejected(output(List.of(1L, 99L), List.of(11L, 12L), List.of(21L), List.of(12L)));
        assertRejected(output(List.of(1L, 2L), List.of(11L, 99L), List.of(21L), List.of(12L)));
        assertRejected(output(List.of(1L, 2L), List.of(11L, 12L), List.of(21L, 99L), List.of(12L)));
    }

    @Test
    void rejectsDuplicateOptionalEvidence() {
        assertRejected(output(List.of(1L, 1L), List.of(11L, 12L), List.of(21L), List.of(12L)));
        assertRejected(output(List.of(1L, 2L), List.of(11L, 11L), List.of(21L), List.of(12L)));
        assertRejected(output(List.of(1L, 2L), List.of(11L, 12L), List.of(21L, 21L), List.of(12L)));
    }

    @Test
    void rejectsMissingUnresolvedGap() {
        assertRejected(output(List.of(1L, 2L), List.of(11L, 12L), List.of(21L), List.of()));
    }

    @Test
    void rejectsUnresolvedGapIdWithoutMatchingContentMarker() {
        assertRejected(output(
                "미확인 정보가 있습니다.",
                List.of(1L, 2L),
                List.of(11L, 12L),
                List.of(21L),
                List.of(12L)
        ));
    }

    private DraftGenerationOutput output(
            List<Long> opinions,
            List<Long> issues,
            List<Long> answers,
            List<Long> unresolved
    ) {
        return output(
                "유효한 본문 [미확인:GAP-12]",
                opinions,
                issues,
                answers,
                unresolved
        );
    }

    private DraftGenerationOutput output(
            String content,
            List<Long> opinions,
            List<Long> issues,
            List<Long> answers,
            List<Long> unresolved
    ) {
        return new DraftGenerationOutput(content, opinions, issues, answers, unresolved);
    }

    private void assertRejected(DraftGenerationOutput output) {
        assertThatThrownBy(() -> validator.validate(output, context))
                .isInstanceOf(StructuredOutputSemanticException.class);
    }
}
