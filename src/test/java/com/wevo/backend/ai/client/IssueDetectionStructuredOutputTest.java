package com.wevo.backend.ai.client;

import com.wevo.backend.ai.dto.model.IssueDetectionOutput;
import com.wevo.backend.ai.service.IssueDetectionOutputDefinition;
import com.wevo.backend.ai.service.IssueDetectionOutputValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IssueDetectionStructuredOutputTest {

    private final StructuredOutputDefinition<IssueDetectionOutput> definition =
            new IssueDetectionOutputDefinition(new IssueDetectionOutputValidator()).get();
    private final StrictStructuredOutputConverter<IssueDetectionOutput> converter =
            new StrictStructuredOutputConverter<>(definition);

    @Test
    void acceptsConflictGapAndEmptyTypedResults() {
        assertSuccess("""
                {"issues":[{
                  "type":"CONFLICT",
                  "description":"목표 기능의 우선순위가 충돌합니다.",
                  "evidenceOpinionIds":[1,2],
                  "question":"어느 기능을 우선할까요?",
                  "options":["탐색","일정 관리"]
                }]}
                """);
        assertSuccess("""
                {"issues":[{
                  "type":"GAP",
                  "description":"예산 근거가 없으며 견적 자료가 필요합니다.",
                  "evidenceOpinionIds":[1],
                  "question":null,
                  "options":[]
                }]}
                """);
        assertSuccess("{\"issues\":[]}");
    }

    @Test
    void schemaRejectsWrongEnumMissingValuesAndUnexpectedActorOrStatus() {
        assertSchemaFailure("""
                {"issues":[{
                  "type":"UNKNOWN",
                  "description":"설명",
                  "evidenceOpinionIds":[1],
                  "question":null,
                  "options":[]
                }]}
                """);
        assertSchemaFailure("""
                {"issues":[{
                  "type":"GAP",
                  "description":"설명",
                  "question":null,
                  "options":[]
                }]}
                """);
        assertSchemaFailure("""
                {"issues":[{
                  "type":"GAP",
                  "description":"설명",
                  "evidenceOpinionIds":[1],
                  "question":null,
                  "options":[],
                  "status":"RESOLVED",
                  "resolvedByUserId":7
                }]}
                """);
    }

    private void assertSuccess(String json) {
        assertThat(converter.convert(json).isSuccess()).isTrue();
    }

    private void assertSchemaFailure(String json) {
        assertThat(converter.convert(json).failure())
                .isEqualTo(StructuredConversionFailure.SCHEMA_VALIDATION);
    }
}
