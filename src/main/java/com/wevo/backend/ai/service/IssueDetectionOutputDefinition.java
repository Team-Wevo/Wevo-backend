package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.OutputSchemaId;
import com.wevo.backend.ai.client.StructuredOutputDefinition;
import com.wevo.backend.ai.dto.model.IssueDetectionOutput;
import org.springframework.stereotype.Component;

/** 쟁점 감지 prompt와 독립적으로 버전 추적되는 출력 schema 정의. */
@Component
public class IssueDetectionOutputDefinition {

    public static final OutputSchemaId SCHEMA_ID = new OutputSchemaId("issue-detection-output", 1);
    private static final String JSON_SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "additionalProperties": false,
              "required": ["issues"],
              "properties": {
                "issues": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "additionalProperties": false,
                    "required": [
                      "type",
                      "description",
                      "evidenceOpinionIds",
                      "question",
                      "options"
                    ],
                    "properties": {
                      "type": {
                        "type": "string",
                        "enum": ["CONFLICT", "GAP"]
                      },
                      "description": {
                        "type": "string"
                      },
                      "evidenceOpinionIds": {
                        "type": "array",
                        "items": {
                          "type": "integer"
                        }
                      },
                      "question": {
                        "type": ["string", "null"]
                      },
                      "options": {
                        "type": "array",
                        "items": {
                          "type": "string"
                        }
                      }
                    }
                  }
                }
              }
            }
            """;

    private final StructuredOutputDefinition<IssueDetectionOutput> definition;

    public IssueDetectionOutputDefinition(IssueDetectionOutputValidator validator) {
        this.definition = StructuredOutputDefinition.of(
                SCHEMA_ID,
                IssueDetectionOutput.class,
                JSON_SCHEMA,
                validator
        );
    }

    public StructuredOutputDefinition<IssueDetectionOutput> get() {
        return definition;
    }
}
