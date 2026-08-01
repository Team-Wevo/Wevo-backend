package com.wevo.backend.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wevo.backend.ai.dto.model.OpinionClusterOutput;
import com.wevo.backend.ai.dto.model.OpinionClusteringOutput;
import com.wevo.backend.ai.service.OpinionClusteringOutputDefinition;
import com.wevo.backend.ai.service.OpinionClusteringOutputValidator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OpinionClusteringStructuredOutputTest {

    private final OpinionClusteringOutputValidator validator =
            new OpinionClusteringOutputValidator();
    private final StructuredOutputDefinition<OpinionClusteringOutput> definition =
            new OpinionClusteringOutputDefinition(validator).get();
    private final StrictStructuredOutputConverter<OpinionClusteringOutput> converter =
            new StrictStructuredOutputConverter<>(definition);

    @Test
    void acceptsOneClusterForAllSimilarAndSingletonsForAllDistinct() {
        validate(new OpinionClusteringOutput(List.of(
                cluster(1, "공통 방향", "세 의견이 같은 방향을 제안합니다.", 1L, 2L, 3L)
        )), Set.of(1L, 2L, 3L));

        validate(new OpinionClusteringOutput(List.of(
                cluster(1, "첫 의견", "독립적인 첫 의견입니다.", 1L),
                cluster(2, "둘째 의견", "독립적인 둘째 의견입니다.", 2L),
                cluster(3, "셋째 의견", "독립적인 셋째 의견입니다.", 3L)
        )), Set.of(1L, 2L, 3L));
    }

    @Test
    void rejectsUnknownDuplicateMissingEmptyAndExceededLimits() {
        assertRejected(new OpinionClusteringOutput(List.of()), Set.of(1L, 2L));
        assertRejected(new OpinionClusteringOutput(List.of(
                cluster(1, "묶음", "요약", 1L, 9L)
        )), Set.of(1L, 2L));
        assertRejected(new OpinionClusteringOutput(List.of(
                cluster(1, "묶음", "요약", 1L),
                cluster(2, "중복", "요약", 1L, 2L)
        )), Set.of(1L, 2L));
        assertRejected(new OpinionClusteringOutput(List.of(
                cluster(1, "묶음", "요약", 1L)
        )), Set.of(1L, 2L));
        assertRejected(new OpinionClusteringOutput(List.of(
                new OpinionClusterOutput(1, " ", "요약", List.of(1L, 2L))
        )), Set.of(1L, 2L));
        assertRejected(new OpinionClusteringOutput(List.of(
                new OpinionClusterOutput(1, "제목", "요약", List.of())
        )), Set.of(1L, 2L));
        assertRejected(new OpinionClusteringOutput(List.of(
                cluster(1, "가".repeat(61), "요약", 1L, 2L)
        )), Set.of(1L, 2L));
        assertRejected(new OpinionClusteringOutput(List.of(
                cluster(1, "제목", "가".repeat(501), 1L, 2L)
        )), Set.of(1L, 2L));
        assertRejected(new OpinionClusteringOutput(List.of(
                cluster(1, "1", "요약", 1L),
                cluster(2, "2", "요약", 2L),
                cluster(3, "3", "요약", 3L),
                cluster(4, "4", "요약", 4L),
                cluster(5, "5", "요약", 5L)
        )), Set.of(1L, 2L, 3L, 4L, 5L));
    }

    @Test
    void schemaRejectsMissingAndUnexpectedFields() {
        assertThat(converter.convert("""
                {"clusters":[{"order":1,"title":"제목","summary":"요약"}]}
                """).failure()).isEqualTo(StructuredConversionFailure.SCHEMA_VALIDATION);
        assertThat(converter.convert("""
                {"clusters":[{
                  "order":1,
                  "title":"제목",
                  "summary":"요약",
                  "opinionIds":[1,2,3],
                  "unexpected":true
                }]}
                """).failure()).isEqualTo(StructuredConversionFailure.SCHEMA_VALIDATION);
    }

    private void validate(OpinionClusteringOutput output, Set<Long> ids) {
        validator.validate(output, new StructuredOutputValidationContext(ids));
    }

    private void assertRejected(OpinionClusteringOutput output, Set<Long> ids) {
        assertThatThrownBy(() -> validate(output, ids))
                .isInstanceOf(StructuredOutputSemanticException.class);
    }

    private OpinionClusterOutput cluster(
            int order, String title, String summary, Long... ids
    ) {
        return new OpinionClusterOutput(order, title, summary, List.of(ids));
    }
}
