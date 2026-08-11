package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.wevo.backend.ai.client.StructuredOutputSemanticException;
import com.wevo.backend.ai.client.StructuredOutputSemanticFailureReason;
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

    @Test
    @DisplayName("allowlist 밖 합의 근거 ID를 필드와 값까지 식별한다")
    void unknownConsensusEvidence_reportsSafeDiagnostics() {
        StructuredOutputSemanticException failure = catchThrowableOfType(
                StructuredOutputSemanticException.class,
                () -> validator.validate(
                        output(List.of(999L), List.of(1L, 2L, 3L), List.of()),
                        context));

        assertThat(failure.getReason())
                .isEqualTo(StructuredOutputSemanticFailureReason.REFERENCE_NOT_ALLOWED);
        assertThat(failure.getField())
                .isEqualTo("consensusEvidenceOpinionIds");
        assertThat(failure.getOffendingResourceId())
                .isEqualTo(999L);
        assertThat(failure.getMessage())
                .doesNotContain("999");
    }

    @Test
    @DisplayName("coverage 누락·중복·추가를 서로 다른 진단 사유로 구분한다")
    void invalidCoverage_reportsDistinctDiagnostics() {
        StructuredOutputSemanticException missing = failureForCoverage(List.of(1L, 2L));
        assertThat(missing.getReason())
                .isEqualTo(StructuredOutputSemanticFailureReason.COVERAGE_REFERENCE_MISSING);
        assertThat(missing.getOffendingResourceId()).isEqualTo(3L);
        assertThat(missing.getExpectedCount()).isEqualTo(3);
        assertThat(missing.getActualCount()).isEqualTo(2);

        StructuredOutputSemanticException duplicated =
                failureForCoverage(List.of(1L, 2L, 3L, 3L));
        assertThat(duplicated.getReason())
                .isEqualTo(StructuredOutputSemanticFailureReason.COVERAGE_REFERENCE_DUPLICATED);
        assertThat(duplicated.getOffendingResourceId()).isEqualTo(3L);

        StructuredOutputSemanticException unexpected =
                failureForCoverage(List.of(1L, 2L, 3L, 999L));
        assertThat(unexpected.getReason())
                .isEqualTo(StructuredOutputSemanticFailureReason.COVERAGE_REFERENCE_UNEXPECTED);
        assertThat(unexpected.getOffendingResourceId()).isEqualTo(999L);
    }

    @Test
    @DisplayName("issue 근거 오류는 issue index가 포함된 필드로 식별한다")
    void unknownIssueEvidence_reportsIndexedField() {
        StructuredOutputSemanticException failure = catchThrowableOfType(
                StructuredOutputSemanticException.class,
                () -> validator.validate(output(
                        List.of(1L),
                        List.of(1L, 2L, 3L),
                        List.of(conflict(List.of(999L)))), context));

        assertThat(failure.getReason())
                .isEqualTo(StructuredOutputSemanticFailureReason.REFERENCE_NOT_ALLOWED);
        assertThat(failure.getField())
                .isEqualTo("issues[0].evidenceOpinionIds");
        assertThat(failure.getOffendingResourceId())
                .isEqualTo(999L);
    }

    @Test
    @DisplayName("GAP의 sourceIssueId와 answerId를 의견 근거로 반환하면 거부한다")
    void gapReferenceIdsCannotBeUsedAsOpinionEvidence() {
        for (Long gapReferenceId : List.of(20L, 30L)) {
            StructuredOutputSemanticException failure = catchThrowableOfType(
                    StructuredOutputSemanticException.class,
                    () -> validator.validate(output(
                            List.of(gapReferenceId),
                            List.of(1L, 2L, 3L),
                            List.of()), context));

            assertThat(failure.getReason())
                    .isEqualTo(StructuredOutputSemanticFailureReason.REFERENCE_NOT_ALLOWED);
            assertThat(failure.getField()).isEqualTo("consensusEvidenceOpinionIds");
            assertThat(failure.getOffendingResourceId()).isEqualTo(gapReferenceId);
        }
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

    private StructuredOutputSemanticException failureForCoverage(List<Long> coverage) {
        return catchThrowableOfType(
                StructuredOutputSemanticException.class,
                () -> validator.validate(output(List.of(1L), coverage, List.of()), context));
    }
}
