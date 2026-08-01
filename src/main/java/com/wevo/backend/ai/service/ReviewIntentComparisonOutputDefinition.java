package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.OutputSchemaId;
import com.wevo.backend.ai.client.StructuredOutputDefinition;
import com.wevo.backend.ai.dto.model.ReviewIntentComparisonOutput;
import org.springframework.stereotype.Component;

@Component
public class ReviewIntentComparisonOutputDefinition {

    public static final OutputSchemaId SCHEMA_ID =
            new OutputSchemaId("review-intent-comparison-output", 1);

    private static final String JSON_SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "additionalProperties": false,
              "required": ["alignment", "differenceSummary", "evidenceExcerpt"],
              "properties": {
                "alignment": {
                  "type": "string",
                  "enum": ["ALIGNED", "PARTIAL", "MISALIGNED"]
                },
                "differenceSummary": {
                  "type": "string",
                  "minLength": 1,
                  "maxLength": %d,
                  "pattern": "^[\\\\s\\\\S]*\\\\S[\\\\s\\\\S]*$"
                },
                "evidenceExcerpt": {
                  "type": ["string", "null"],
                  "minLength": 1,
                  "maxLength": %d,
                  "pattern": "^[\\\\s\\\\S]*\\\\S[\\\\s\\\\S]*$"
                }
              }
            }
            """.formatted(
            ReviewIntentComparisonOutputValidator.MAX_DIFFERENCE_LENGTH,
            ReviewIntentComparisonOutputValidator.MAX_EVIDENCE_LENGTH);

    private final StructuredOutputDefinition<ReviewIntentComparisonOutput> definition;

    public ReviewIntentComparisonOutputDefinition(ReviewIntentComparisonOutputValidator validator) {
        this.definition = StructuredOutputDefinition.of(
                SCHEMA_ID,
                ReviewIntentComparisonOutput.class,
                JSON_SCHEMA,
                validator);
    }

    public StructuredOutputDefinition<ReviewIntentComparisonOutput> get() {
        return definition;
    }
}
