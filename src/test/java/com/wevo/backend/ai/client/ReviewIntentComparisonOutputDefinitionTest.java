package com.wevo.backend.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wevo.backend.ai.dto.model.ReviewIntentComparisonOutput;
import com.wevo.backend.ai.service.ReviewIntentComparisonOutputDefinition;
import com.wevo.backend.ai.service.ReviewIntentComparisonOutputValidator;
import com.wevo.backend.review.domain.ReviewIntentAlignment;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReviewIntentComparisonOutputDefinitionTest {

    private static final String INTENT = "팀 의견을 하나의 제안으로 정리한다.";
    private static final String SUMMARY = "여러 의견을 제안으로 합치는 서비스다.";

    private final ReviewIntentComparisonOutputValidator validator =
            new ReviewIntentComparisonOutputValidator();
    private final StructuredOutputDefinition<ReviewIntentComparisonOutput> definition =
            new ReviewIntentComparisonOutputDefinition(validator).get();
    private final StrictStructuredOutputConverter<ReviewIntentComparisonOutput> converter =
            new StrictStructuredOutputConverter<>(definition);
    private final StructuredOutputValidationContext context =
            StructuredOutputValidationContext.forSourceContents(
                    SUMMARY, Set.of(INTENT, SUMMARY));

    @Test
    void acceptsAllAlignmentEnumsAndNullableEvidence() {
        for (ReviewIntentAlignment alignment : ReviewIntentAlignment.values()) {
            assertThatCode(() -> validator.validate(
                    new ReviewIntentComparisonOutput(
                            alignment, "핵심 의미의 전달 정도를 비교했다.", null),
                    context)).doesNotThrowAnyException();
        }
        assertThat(converter.convert("""
                {
                  "alignment":"PARTIAL",
                  "differenceSummary":"대상 범위가 일부 누락되었다.",
                  "evidenceExcerpt":null
                }
                """).isSuccess()).isTrue();
    }

    @Test
    void rejectsMalformedMissingUnknownEnumBlankAndExtraFields() {
        assertFailure("not-json");
        assertFailure("{\"alignment\":\"ALIGNED\"}");
        assertFailure("""
                {"alignment":"SAME","differenceSummary":"설명","evidenceExcerpt":null}
                """);
        assertFailure("""
                {"alignment":"ALIGNED","differenceSummary":" ","evidenceExcerpt":null}
                """);
        assertFailure("""
                {"alignment":"ALIGNED","differenceSummary":"설명","evidenceExcerpt":null,"extra":1}
                """);
    }

    @Test
    void semanticValidatorRejectsEvidenceNotCopiedFromEitherInput() {
        assertThatThrownBy(() -> validator.validate(
                new ReviewIntentComparisonOutput(
                        ReviewIntentAlignment.MISALIGNED,
                        "의미가 다르다.",
                        "입력에 없는 근거"),
                context)).isInstanceOf(StructuredOutputSemanticException.class);
    }

    private void assertFailure(String json) {
        assertThat(converter.convert(json).isSuccess()).isFalse();
    }
}
