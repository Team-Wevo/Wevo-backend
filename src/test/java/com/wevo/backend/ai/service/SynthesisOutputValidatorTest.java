package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wevo.backend.ai.client.StructuredOutputSemanticException;
import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.service.SynthesisAiOutput.IssueOut;
import com.wevo.backend.issue.domain.IssueType;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SynthesisOutputValidatorTest {

    private final SynthesisOutputValidator validator = new SynthesisOutputValidator();
    private final StructuredOutputValidationContext context =
            new StructuredOutputValidationContext(Set.of(1L, 2L, 3L));

    private IssueOut conflict(List<Long> relatedOpinionIds) {
        return new IssueOut(IssueType.CONFLICT, "방향이 갈립니다.", "무엇으로 정할까요?",
                List.of("선택지 A", "선택지 B"), relatedOpinionIds);
    }

    private IssueOut gap(List<Long> relatedOpinionIds) {
        return new IssueOut(IssueType.GAP, "정보가 부족합니다.", "", List.of(), relatedOpinionIds);
    }

    private void validate(SynthesisAiOutput output) {
        validator.validate(output, context);
    }

    @Test
    @DisplayName("합의점과 유효한 쟁점은 통과한다")
    void validate_validOutput_passes() {
        SynthesisAiOutput output = new SynthesisAiOutput(
                "타겟을 좁히는 데 대체로 동의합니다.",
                List.of(conflict(List.of(1L, 2L)), gap(List.of(3L))));

        assertThatCode(() -> validate(output)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("합의점이 공백이면 거부한다")
    void validate_blankConsensus_rejected() {
        SynthesisAiOutput output = new SynthesisAiOutput("  ", List.of(conflict(List.of(1L))));

        assertThatThrownBy(() -> validate(output))
                .isInstanceOf(StructuredOutputSemanticException.class);
    }

    @Test
    @DisplayName("쟁점이 4개를 넘으면 거부한다")
    void validate_tooManyIssues_rejected() {
        SynthesisAiOutput output = new SynthesisAiOutput("합의점", List.of(
                gap(List.of(1L)), gap(List.of(2L)),
                conflict(List.of(1L)), conflict(List.of(2L)), conflict(List.of(3L))));

        assertThatThrownBy(() -> validate(output))
                .isInstanceOf(StructuredOutputSemanticException.class);
    }

    @Test
    @DisplayName("쟁점 배열에 null 원소가 있으면 의미 검증 실패로 거부한다")
    void validate_nullIssue_rejectedAsSemanticFailure() {
        SynthesisAiOutput output = new SynthesisAiOutput(
                "합의점",
                Collections.singletonList(null));

        assertThatThrownBy(() -> validate(output))
                .isInstanceOf(StructuredOutputSemanticException.class);
    }

    @Test
    @DisplayName("CONFLICT가 3개를 넘으면 거부한다")
    void validate_tooManyConflicts_rejected() {
        SynthesisAiOutput output = new SynthesisAiOutput("합의점", List.of(
                conflict(List.of(1L)), conflict(List.of(2L)),
                conflict(List.of(3L)), conflict(List.of(1L))));

        assertThatThrownBy(() -> validate(output))
                .isInstanceOf(StructuredOutputSemanticException.class);
    }

    @Test
    @DisplayName("GAP이 2개를 넘으면 거부한다")
    void validate_tooManyGaps_rejected() {
        SynthesisAiOutput output = new SynthesisAiOutput("합의점", List.of(
                gap(List.of(1L)), gap(List.of(2L)), gap(List.of(3L))));

        assertThatThrownBy(() -> validate(output))
                .isInstanceOf(StructuredOutputSemanticException.class);
    }

    @Test
    @DisplayName("CONFLICT 선택지가 2개 미만이면 거부한다")
    void validate_conflictWithTooFewOptions_rejected() {
        SynthesisAiOutput output = new SynthesisAiOutput("합의점", List.of(
                new IssueOut(IssueType.CONFLICT, "설명", "질문", List.of("하나뿐"), List.of(1L))));

        assertThatThrownBy(() -> validate(output))
                .isInstanceOf(StructuredOutputSemanticException.class);
    }

    @Test
    @DisplayName("CONFLICT 질문이 공백이면 거부한다")
    void validate_conflictWithBlankQuestion_rejected() {
        SynthesisAiOutput output = new SynthesisAiOutput("합의점", List.of(
                new IssueOut(IssueType.CONFLICT, "설명", "  ",
                        List.of("A", "B"), List.of(1L))));

        assertThatThrownBy(() -> validate(output))
                .isInstanceOf(StructuredOutputSemanticException.class);
    }

    @Test
    @DisplayName("GAP에 선택지가 있으면 거부한다")
    void validate_gapWithOptions_rejected() {
        SynthesisAiOutput output = new SynthesisAiOutput("합의점", List.of(
                new IssueOut(IssueType.GAP, "설명", "", List.of("있으면 안 됨"), List.of(1L))));

        assertThatThrownBy(() -> validate(output))
                .isInstanceOf(StructuredOutputSemanticException.class);
    }

    @Test
    @DisplayName("GAP에 질문이 있으면 거부한다 (질문은 CONFLICT만)")
    void validate_gapWithQuestion_rejected() {
        SynthesisAiOutput output = new SynthesisAiOutput("합의점", List.of(
                new IssueOut(IssueType.GAP, "설명", "있으면 안 되는 질문", List.of(), List.of(1L))));

        assertThatThrownBy(() -> validate(output))
                .isInstanceOf(StructuredOutputSemanticException.class);
    }

    @Test
    @DisplayName("허용 집합 밖의 의견을 참조하면 거부한다")
    void validate_unknownRelatedOpinion_rejected() {
        SynthesisAiOutput output = new SynthesisAiOutput("합의점", List.of(conflict(List.of(999L))));

        assertThatThrownBy(() -> validate(output))
                .isInstanceOf(StructuredOutputSemanticException.class);
    }

    @Test
    @DisplayName("근거 의견이 하나도 없으면 거부한다")
    void validate_noRelatedOpinion_rejected() {
        SynthesisAiOutput output = new SynthesisAiOutput("합의점", List.of(conflict(List.of())));

        assertThatThrownBy(() -> validate(output))
                .isInstanceOf(StructuredOutputSemanticException.class);
    }
}
