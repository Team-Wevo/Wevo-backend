package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.OutputSchemaId;
import com.wevo.backend.ai.client.StructuredOutputDefinition;
import org.springframework.stereotype.Component;

/** opinion synthesis의 필수 필드와 추가 속성 금지를 명시하는 버전형 출력 스키마. */
@Component
public class SynthesisOutputDefinition {

    public static final OutputSchemaId SCHEMA_ID =
            new OutputSchemaId("opinion-synthesis-output", 2);

    private static final String JSON_SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "additionalProperties": false,
              "required": [
                "consensusSummary",
                "consensusEvidenceOpinionIds",
                "coveredOpinionIds",
                "issues"
              ],
              "properties": {
                "consensusSummary": {"type": "string"},
                "consensusEvidenceOpinionIds": {
                  "type": "array",
                  "items": {"type": "integer"}
                },
                "coveredOpinionIds": {
                  "type": "array",
                  "items": {"type": "integer"}
                },
                "issues": %s
              }
            }
            """.formatted(IssueDetectionOutputDefinition.ISSUE_ARRAY_SCHEMA);

    private final StructuredOutputDefinition<SynthesisAiOutput> definition;

    public SynthesisOutputDefinition(SynthesisOutputValidator validator) {
        this.definition = StructuredOutputDefinition.of(
                SCHEMA_ID,
                SynthesisAiOutput.class,
                JSON_SCHEMA,
                validator
        );
    }

    public StructuredOutputDefinition<SynthesisAiOutput> get() {
        return definition;
    }
}
