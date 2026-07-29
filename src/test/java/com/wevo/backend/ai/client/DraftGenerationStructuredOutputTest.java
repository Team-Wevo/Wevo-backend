package com.wevo.backend.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.wevo.backend.ai.service.DraftGenerationOutput;
import com.wevo.backend.ai.service.DraftGenerationOutputDefinition;
import com.wevo.backend.ai.service.DraftGenerationOutputValidator;
import org.junit.jupiter.api.Test;

class DraftGenerationStructuredOutputTest {

    private final StructuredOutputDefinition<DraftGenerationOutput> definition =
            new DraftGenerationOutputDefinition(new DraftGenerationOutputValidator()).get();
    private final StrictStructuredOutputConverter<DraftGenerationOutput> converter =
            new StrictStructuredOutputConverter<>(definition);

    @Test
    void acceptsCompleteDraftContract() {
        assertThat(converter.convert("""
                {
                  "content":"## 문제 정의\\n검증된 합의와 결정입니다.",
                  "evidenceOpinionIds":[1,2],
                  "evidenceIssueIds":[11,12],
                  "evidenceAnswerIds":[21],
                  "unresolvedGapIssueIds":[12]
                }
                """).isSuccess()).isTrue();
    }

    @Test
    void rejectsBlankOversizedMissingAndUnexpectedFieldsAsSchemaFailures() {
        assertSchemaFailure("""
                {
                  "content":"   ",
                  "evidenceOpinionIds":[],
                  "evidenceIssueIds":[],
                  "evidenceAnswerIds":[],
                  "unresolvedGapIssueIds":[]
                }
                """);
        assertSchemaFailure("""
                {
                  "content":"본문",
                  "evidenceOpinionIds":[],
                  "evidenceIssueIds":[],
                  "evidenceAnswerIds":[]
                }
                """);
        assertSchemaFailure("""
                {
                  "content":"본문",
                  "evidenceOpinionIds":[],
                  "evidenceIssueIds":[],
                  "evidenceAnswerIds":[],
                  "unresolvedGapIssueIds":[],
                  "unknown":"value"
                }
                """);
        String oversized = "가".repeat(10_001);
        assertSchemaFailure("""
                {
                  "content":"%s",
                  "evidenceOpinionIds":[],
                  "evidenceIssueIds":[],
                  "evidenceAnswerIds":[],
                  "unresolvedGapIssueIds":[]
                }
                """.formatted(oversized));
    }

    private void assertSchemaFailure(String json) {
        assertThat(converter.convert(json).failure())
                .isEqualTo(StructuredConversionFailure.SCHEMA_VALIDATION);
    }
}
