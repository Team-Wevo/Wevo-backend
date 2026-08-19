package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.OutputSchemaId;
import com.wevo.backend.ai.client.StructuredOutputDefinition;
import com.wevo.backend.ai.dto.model.OpinionGuardrailVerdict;
import org.springframework.stereotype.Component;

/** acceptable(boolean)·reason(enum) 만 허용하는 의견 가드레일 strict schema. */
@Component
public class OpinionGuardrailOutputDefinition {

    public static final OutputSchemaId SCHEMA_ID =
            new OutputSchemaId("opinion-guardrail-output", 1);

    private static final String JSON_SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "additionalProperties": false,
              "required": ["acceptable", "reason"],
              "properties": {
                "acceptable": { "type": "boolean" },
                "reason": { "type": "string", "enum": ["OK", "GIBBERISH", "OFF_TOPIC"] }
              }
            }
            """;

    private final StructuredOutputDefinition<OpinionGuardrailVerdict> definition;

    public OpinionGuardrailOutputDefinition(OpinionGuardrailOutputValidator validator) {
        this.definition = StructuredOutputDefinition.of(
                SCHEMA_ID,
                OpinionGuardrailVerdict.class,
                JSON_SCHEMA,
                validator);
    }

    public StructuredOutputDefinition<OpinionGuardrailVerdict> get() {
        return definition;
    }
}
