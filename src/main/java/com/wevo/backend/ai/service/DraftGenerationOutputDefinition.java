package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.OutputSchemaId;
import com.wevo.backend.ai.client.StructuredOutputDefinition;
import com.wevo.backend.section.dto.request.SectionDraftSaveRequest;
import org.springframework.stereotype.Component;

/** 초안 생성 본문과 aggregate evidence ID의 엄격한 구조화 출력 스키마. */
@Component
public class DraftGenerationOutputDefinition {

    public static final OutputSchemaId SCHEMA_ID =
            new OutputSchemaId("draft-generation-output", 2);

    private static final String ID_ARRAY = """
            {
              "type": "array",
              "items": {"type": "integer", "minimum": 1},
              "uniqueItems": true
            }
            """;

    private static final String JSON_SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "additionalProperties": false,
              "required": [
                "content",
                "evidenceOpinionIds",
                "evidenceIssueIds",
                "evidenceAnswerIds",
                "unresolvedGapIssueIds"
              ],
              "properties": {
                "content": {
                  "type": "string",
                  "minLength": 1,
                  "maxLength": %d,
                  "pattern": "^[\\\\s\\\\S]*\\\\S[\\\\s\\\\S]*$"
                },
                "evidenceOpinionIds": %s,
                "evidenceIssueIds": %s,
                "evidenceAnswerIds": %s,
                "unresolvedGapIssueIds": %s
              }
            }
            """.formatted(
            SectionDraftSaveRequest.MAX_CONTENT_LENGTH,
            ID_ARRAY,
            ID_ARRAY,
            ID_ARRAY,
            ID_ARRAY
    );

    private final StructuredOutputDefinition<DraftGenerationOutput> definition;

    public DraftGenerationOutputDefinition(DraftGenerationOutputValidator validator) {
        definition = StructuredOutputDefinition.of(
                SCHEMA_ID,
                DraftGenerationOutput.class,
                JSON_SCHEMA,
                validator
        );
    }

    public StructuredOutputDefinition<DraftGenerationOutput> get() {
        return definition;
    }
}
