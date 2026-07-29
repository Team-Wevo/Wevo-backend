package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wevo.backend.ai.client.StructuredOutputSemanticException;
import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.dto.model.IssueDetectionIssueOutput;
import com.wevo.backend.issue.domain.IssueType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SynthesisOutputValidatorTest {

    private final SynthesisOutputValidator validator =
            new SynthesisOutputValidator(new IssueDetectionOutputValidator());
    private final StructuredOutputValidationContext context =
            new StructuredOutputValidationContext(Set.of(1L, 2L, 3L));

    @Test
    @DisplayName("합의 근거·전체 coverage와 AI-06 쟁점 계약이 유효하면 통과한다")
    void validOutput_passes() {
        SynthesisAiOutput output = output(
                List.of(1L, 2L),
                List.of(1L, 2L, 3L),
                List.of(
                        conflict(List.of(1L, 2L)),
                        gap(List.of(3L))
                )
        );

        assertThatCode(() -> validator.validate(output, context)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("합의점이 공백이거나 합의 근거가 없으면 거부한다")
    void blankConsensusOrMissingEvidence_rejected() {
        assertRejected(new SynthesisAiOutput(
                " ", List.of(1L), List.of(1L, 2L, 3L), List.of()));
        assertRejected(new SynthesisAiOutput(
                "합의점", List.of(), List.of(1L, 2L, 3L), List.of()));
    }

    @Test
    @DisplayName("합의 근거의 중복·allowlist 밖 ID를 거부한다")
    void invalidConsensusEvidence_rejected() {
        assertRejected(output(List.of(1L, 1L), List.of(1L, 2L, 3L), List.of()));
        assertRejected(output(List.of(999L), List.of(1L, 2L, 3L), List.of()));
    }

    @Test
    @DisplayName("coverage가 eligible opinion을 누락·중복·추가하면 거부한다")
    void incompleteOrInvalidCoverage_rejected() {
        assertRejected(output(List.of(1L), List.of(1L, 2L), List.of()));
        assertRejected(output(List.of(1L), List.of(1L, 2L, 3L, 3L), List.of()));
        assertRejected(output(List.of(1L), List.of(1L, 2L, 3L, 999L), List.of()));
    }

    @Test
    @DisplayName("쟁점은 AI-06 단일 계약으로 검증한다")
    void issueContract_isSharedWithIssueDetection() {
        assertRejected(output(List.of(1L), List.of(1L, 2L, 3L), List.of(
                new IssueDetectionIssueOutput(
                        IssueType.GAP,
                        "근거 부족",
                        List.of(1L),
                        "GAP에 있으면 안 되는 질문",
                        List.of()
                )
        )));
        assertRejected(output(List.of(1L), List.of(1L, 2L, 3L), List.of(
                conflict(List.of(999L))
        )));
    }

    private SynthesisAiOutput output(
            List<Long> consensusEvidence,
            List<Long> coverage,
            List<IssueDetectionIssueOutput> issues
    ) {
        return new SynthesisAiOutput("합의점", consensusEvidence, coverage, issues);
    }

    private IssueDetectionIssueOutput conflict(List<Long> evidence) {
        return new IssueDetectionIssueOutput(
                IssueType.CONFLICT,
                "방향이 갈립니다.",
                evidence,
                "무엇으로 정할까요?",
                List.of("선택지 A", "선택지 B")
        );
    }

    private IssueDetectionIssueOutput gap(List<Long> evidence) {
        return new IssueDetectionIssueOutput(
                IssueType.GAP,
                "정보가 부족합니다.",
                evidence,
                null,
                List.of()
        );
    }

    private void assertRejected(SynthesisAiOutput output) {
        assertThatThrownBy(() -> validator.validate(output, context))
                .isInstanceOf(StructuredOutputSemanticException.class);
    }
}
