package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.OutputSchemaId;
import com.wevo.backend.ai.client.StructuredOutputDefinition;
import com.wevo.backend.ai.dto.model.ProjectTitleSuggestionOutput;
import org.springframework.stereotype.Component;

/** extra field·공백·개행·길이 초과를 거부하는 프로젝트 제목 strict schema. */
@Component
public class ProjectTitleOutputDefinition {

    public static final OutputSchemaId SCHEMA_ID =
            new OutputSchemaId("project-title-output", 1);

    private static final String JSON_SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "additionalProperties": false,
              "required": ["title"],
              "properties": {
                "title": {
                  "type": "string",
                  "minLength": 1,
                  "maxLength": %d,
                  "pattern": "^[^\\\\r\\\\n]*\\\\S[^\\\\r\\\\n]*$"
                }
              }
            }
            """.formatted(ProjectTitleContract.MAX_TITLE_LENGTH);

    private final StructuredOutputDefinition<ProjectTitleSuggestionOutput> definition;

    public ProjectTitleOutputDefinition(ProjectTitleOutputValidator validator) {
        this.definition = StructuredOutputDefinition.of(
                SCHEMA_ID,
                ProjectTitleSuggestionOutput.class,
                JSON_SCHEMA,
                validator);
    }

    public StructuredOutputDefinition<ProjectTitleSuggestionOutput> get() {
        return definition;
    }
}
