package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.OutputSchemaId;
import com.wevo.backend.ai.client.StructuredOutputDefinition;
import com.wevo.backend.ai.dto.model.OpinionClusteringOutput;
import org.springframework.stereotype.Component;

/** 의견 분류 strict JSON schema. 모든 object에서 예상하지 않은 필드를 금지한다. */
@Component
public class OpinionClusteringOutputDefinition {

    public static final OutputSchemaId SCHEMA_ID =
            new OutputSchemaId("opinion-clustering-output", 1);

    private static final String JSON_SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "additionalProperties": false,
              "required": ["clusters"],
              "properties": {
                "clusters": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "additionalProperties": false,
                    "required": ["order", "title", "summary", "opinionIds"],
                    "properties": {
                      "order": {"type": "integer"},
                      "title": {"type": "string"},
                      "summary": {"type": "string"},
                      "opinionIds": {
                        "type": "array",
                        "items": {"type": "integer"}
                      }
                    }
                  }
                }
              }
            }
            """;

    private final StructuredOutputDefinition<OpinionClusteringOutput> definition;

    public OpinionClusteringOutputDefinition(OpinionClusteringOutputValidator validator) {
        definition = StructuredOutputDefinition.of(
                SCHEMA_ID,
                OpinionClusteringOutput.class,
                JSON_SCHEMA,
                validator
        );
    }

    public StructuredOutputDefinition<OpinionClusteringOutput> get() {
        return definition;
    }
}
