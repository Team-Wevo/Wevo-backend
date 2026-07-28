package com.wevo.backend.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.wevo.backend.ai.dto.model.DraftReviewOutput;
import com.wevo.backend.ai.service.DraftReviewOutputDefinition;
import com.wevo.backend.ai.service.DraftReviewOutputValidator;
import org.junit.jupiter.api.Test;

class DraftReviewOutputDefinitionTest {

    private final StructuredOutputDefinition<DraftReviewOutput> definition =
            new DraftReviewOutputDefinition(new DraftReviewOutputValidator()).get();
    private final StrictStructuredOutputConverter<DraftReviewOutput> converter =
            new StrictStructuredOutputConverter<>(definition);

    @Test
    void acceptsStrictFindingAndRewriteSchema() {
        StructuredConversionResult<DraftReviewOutput> result = converter.convert("""
                {
                  "findings": [{
                    "type": "UNCLEAR_SENTENCE",
                    "targetExcerpt": "모호한 문장",
                    "comment": "의미가 여러 가지입니다.",
                    "suggestion": "대상을 명시하세요."
                  }],
                  "rewrite": {"content": "개선한 본문", "changedCount": 1}
                }
                """);

        assertThat(result.isSuccess()).isTrue();
        assertThat(definition.schemaId().trackingValue())
                .isEqualTo("draft-review-output:v1");
    }

    @Test
    void rejectsWrongSchemaMissingValuesBlankAndOversizedRewrite() {
        assertSchemaFailure("""
                {"findings": [], "rewrite": {"content": "본문"}}
                """);
        assertSchemaFailure("""
                {
                  "findings": [{
                    "type": "SEVERE",
                    "targetExcerpt": "문장",
                    "comment": "의견",
                    "suggestion": "제안"
                  }],
                  "rewrite": {"content": "본문", "changedCount": 1}
                }
                """);
        assertSchemaFailure("""
                {"findings": [], "rewrite": {"content": "   ", "changedCount": 0}}
                """);
        assertSchemaFailure("""
                {"findings": [], "rewrite": {"content": "%s", "changedCount": 0}}
                """.formatted("a".repeat(10_001)));
    }

    private void assertSchemaFailure(String json) {
        assertThat(converter.convert(json).failure())
                .isEqualTo(StructuredConversionFailure.SCHEMA_VALIDATION);
    }
}
