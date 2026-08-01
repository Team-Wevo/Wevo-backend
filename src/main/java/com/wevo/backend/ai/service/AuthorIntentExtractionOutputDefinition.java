package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.OutputSchemaId;
import com.wevo.backend.ai.client.StructuredOutputDefinition;
import com.wevo.backend.ai.dto.model.AuthorIntentExtractionOutput;
import com.wevo.backend.section.domain.AuthorIntentTextPolicy;
import org.springframework.stereotype.Component;

/** extra field·공백·개행·길이 초과를 거부하는 작성자 의도 strict schema. */
@Component
public class AuthorIntentExtractionOutputDefinition {

    public static final OutputSchemaId SCHEMA_ID =
            new OutputSchemaId("author-intent-output", 1);

    private static final String JSON_SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "additionalProperties": false,
              "required": ["intent"],
              "properties": {
                "intent": {
                  "type": "string",
                  "minLength": 1,
                  "maxLength": %d,
                  "pattern": "^[^\\\\r\\\\n]*\\\\S[^\\\\r\\\\n]*$"
                }
              }
            }
            """.formatted(AuthorIntentTextPolicy.MAX_LENGTH);

    private final StructuredOutputDefinition<AuthorIntentExtractionOutput> definition;

    public AuthorIntentExtractionOutputDefinition(AuthorIntentExtractionOutputValidator validator) {
        this.definition = StructuredOutputDefinition.of(
                SCHEMA_ID,
                AuthorIntentExtractionOutput.class,
                JSON_SCHEMA,
                validator);
    }

    public StructuredOutputDefinition<AuthorIntentExtractionOutput> get() {
        return definition;
    }
}
