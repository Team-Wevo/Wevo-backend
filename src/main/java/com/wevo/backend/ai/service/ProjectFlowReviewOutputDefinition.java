package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.OutputSchemaId;
import com.wevo.backend.ai.client.StructuredOutputDefinition;
import com.wevo.backend.ai.dto.model.ProjectFlowReviewOutput;
import org.springframework.stereotype.Component;

@Component
public class ProjectFlowReviewOutputDefinition {
    public static final OutputSchemaId SCHEMA_ID = new OutputSchemaId("project-flow-review-output", 1);
    private static final String JSON_SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "additionalProperties": false,
              "required": ["findings"],
              "properties": {
                "findings": {
                  "type": "array",
                  "maxItems": 50,
                  "items": {
                    "type": "object",
                    "additionalProperties": false,
                    "required": ["type", "sections", "description", "suggestion"],
                    "properties": {
                      "type": {"type": "string", "enum": [
                        "CLAIM_OR_NUMBER_CONTRADICTION", "LOGICAL_CONNECTION_MISSING",
                        "REDUNDANT_CONTENT", "TERMINOLOGY_AUDIENCE_TONE_MISMATCH",
                        "DEPENDENCY_NOT_REFLECTED"
                      ]},
                      "sections": {
                        "type": "array", "minItems": 2,
                        "items": {
                          "type": "object", "additionalProperties": false,
                          "required": ["sectionId", "targetExcerpt"],
                          "properties": {
                            "sectionId": {"type": "integer"},
                            "targetExcerpt": {"type": "string", "maxLength": 500}
                          }
                        }
                      },
                      "description": {"type": "string", "maxLength": 1000},
                      "suggestion": {"type": "string", "maxLength": 1000}
                    }
                  }
                }
              }
            }
            """;

    private final StructuredOutputDefinition<ProjectFlowReviewOutput> definition;

    public ProjectFlowReviewOutputDefinition(ProjectFlowReviewOutputValidator validator) {
        definition = StructuredOutputDefinition.of(SCHEMA_ID, ProjectFlowReviewOutput.class,
                JSON_SCHEMA, validator);
    }

    public StructuredOutputDefinition<ProjectFlowReviewOutput> get() {
        return definition;
    }
}
