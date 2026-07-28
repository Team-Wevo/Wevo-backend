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
                output(List.of(2L, 1L), List.of(12L, 11L), List.of(21L), List.of(12L)),
                context
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnknownMissingDuplicateEvidenceAndMissingUnresolvedGap() {
        assertRejected(output(List.of(1L, 99L), List.of(11L, 12L), List.of(21L), List.of(12L)));
        assertRejected(output(List.of(1L), List.of(11L, 12L), List.of(21L), List.of(12L)));
        assertRejected(output(List.of(1L, 1L), List.of(11L, 12L), List.of(21L), List.of(12L)));
        assertRejected(output(List.of(1L, 2L), List.of(11L, 12L), List.of(21L), List.of()));
    }

    private DraftGenerationOutput output(
            List<Long> opinions,
            List<Long> issues,
            List<Long> answers,
            List<Long> unresolved
    ) {
        return new DraftGenerationOutput("유효한 본문", opinions, issues, answers, unresolved);
    }

    private void assertRejected(DraftGenerationOutput output) {
        assertThatThrownBy(() -> validator.validate(output, context))
                .isInstanceOf(StructuredOutputSemanticException.class);
    }
}
