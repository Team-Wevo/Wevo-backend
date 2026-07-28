package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.OutputSchemaId;
import com.wevo.backend.ai.client.StructuredOutputDefinition;
import com.wevo.backend.ai.dto.model.DraftReviewOutput;
import com.wevo.backend.section.dto.request.SectionDraftSaveRequest;
import org.springframework.stereotype.Component;

/** findings 네 유형과 전체 rewrite의 엄격한 JSON Schema. */
@Component
public class DraftReviewOutputDefinition {

    public static final OutputSchemaId SCHEMA_ID =
            new OutputSchemaId("draft-review-output", 1);

    private static final String NON_BLANK_TEXT = """
            {
              "type": "string",
              "minLength": 1,
              "pattern": "^[\\\\s\\\\S]*\\\\S[\\\\s\\\\S]*$"
            }
            """;

    private static final String JSON_SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "additionalProperties": false,
              "required": ["findings", "rewrite"],
              "properties": {
                "findings": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "additionalProperties": false,
                    "required": ["type", "targetExcerpt", "comment", "suggestion"],
                    "properties": {
                      "type": {
                        "type": "string",
                        "enum": [
                          "UNCLEAR_SENTENCE",
                          "HIDDEN_ASSUMPTION",
                          "PREREQUISITE_CONFLICT",
                          "READER_QUESTION"
                        ]
                      },
                      "targetExcerpt": %s,
                      "comment": %s,
                      "suggestion": %s
                    }
                  }
                },
                "rewrite": {
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["content", "changedCount"],
                  "properties": {
                    "content": {
                      "type": "string",
                      "minLength": 1,
                      "maxLength": %d,
                      "pattern": "^[\\\\s\\\\S]*\\\\S[\\\\s\\\\S]*$"
                    },
                    "changedCount": {
                      "type": "integer",
                      "minimum": 0
                    }
                  }
                }
              }
            }
            """.formatted(
            NON_BLANK_TEXT,
            NON_BLANK_TEXT,
            NON_BLANK_TEXT,
            SectionDraftSaveRequest.MAX_CONTENT_LENGTH
    );

    private final StructuredOutputDefinition<DraftReviewOutput> definition;

    public DraftReviewOutputDefinition(DraftReviewOutputValidator validator) {
        definition = StructuredOutputDefinition.of(
                SCHEMA_ID,
                DraftReviewOutput.class,
                JSON_SCHEMA,
                validator
        );
    }

    public StructuredOutputDefinition<DraftReviewOutput> get() {
        return definition;
    }
}
