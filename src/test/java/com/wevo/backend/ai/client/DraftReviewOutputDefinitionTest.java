package com.wevo.backend.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.wevo.backend.ai.domain.AiSectionCheckFinding;
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
        assertFindingTextSchemaFailure(
                "targetExcerpt",
                "a".repeat(AiSectionCheckFinding.MAX_TARGET_EXCERPT_LENGTH + 1));
        assertFindingTextSchemaFailure(
                "comment",
                "a".repeat(AiSectionCheckFinding.MAX_COMMENT_LENGTH + 1));
        assertFindingTextSchemaFailure(
                "suggestion",
                "a".repeat(AiSectionCheckFinding.MAX_SUGGESTION_LENGTH + 1));
    }

    private void assertFindingTextSchemaFailure(String field, String value) {
        String targetExcerpt = field.equals("targetExcerpt") ? value : "문장";
        String comment = field.equals("comment") ? value : "의견";
        String suggestion = field.equals("suggestion") ? value : "제안";
        assertSchemaFailure("""
                {
                  "findings": [{
                    "type": "UNCLEAR_SENTENCE",
                    "targetExcerpt": "%s",
                    "comment": "%s",
                    "suggestion": "%s"
                  }],
                  "rewrite": {"content": "본문", "changedCount": 1}
                }
                """.formatted(targetExcerpt, comment, suggestion));
    }

    private void assertSchemaFailure(String json) {
        assertThat(converter.convert(json).failure())
                .isEqualTo(StructuredConversionFailure.SCHEMA_VALIDATION);
    }
}
