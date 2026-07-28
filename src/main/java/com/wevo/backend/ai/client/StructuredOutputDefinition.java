package com.wevo.backend.ai.client;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class StructuredOutputDefinition<T> {

    private final OutputSchemaId schemaId;
    private final Class<T> outputType;
    private final String jsonSchema;
    private final JsonMapper jsonMapper;
    private final Schema compiledSchema;
    private final StructuredOutputValidator<T> validator;

    private StructuredOutputDefinition(
            OutputSchemaId schemaId,
            Class<T> outputType,
            String jsonSchema,
            StructuredOutputValidator<T> validator
    ) {
        if (schemaId == null || outputType == null || jsonSchema == null || jsonSchema.isBlank() || validator == null) {
            throw new IllegalArgumentException("schema id, output type, JSON schema, validator는 필수입니다.");
        }
        if (!outputType.isRecord()) {
            throw new IllegalArgumentException("AI 구조화 출력 타입은 Java record여야 합니다.");
        }
        this.schemaId = schemaId;
        this.outputType = outputType;
        this.jsonSchema = jsonSchema;
        this.validator = validator;
        this.jsonMapper = JacksonUtils.getDefaultJsonMapper();
        try {
            JsonNode schemaNode = jsonMapper.readTree(jsonSchema);
            this.compiledSchema = SchemaRegistry
                    .withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                    .getSchema(schemaNode);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("JSON schema가 올바르지 않습니다.", exception);
        }
    }

    public static <T> StructuredOutputDefinition<T> of(
            OutputSchemaId schemaId,
            Class<T> outputType,
            StructuredOutputValidator<T> validator
    ) {
        return new StructuredOutputDefinition<>(
                schemaId,
                outputType,
                JsonSchemaGenerator.generateForType(outputType),
                validator
        );
    }

    public static <T> StructuredOutputDefinition<T> of(OutputSchemaId schemaId, Class<T> outputType) {
        return of(schemaId, outputType, StructuredOutputValidator.noOp());
    }

    public static <T> StructuredOutputDefinition<T> of(
            OutputSchemaId schemaId,
            Class<T> outputType,
            String jsonSchema,
            StructuredOutputValidator<T> validator
    ) {
        return new StructuredOutputDefinition<>(schemaId, outputType, jsonSchema, validator);
    }

    static <T> StructuredOutputDefinition<T> withSchema(
            OutputSchemaId schemaId,
            Class<T> outputType,
            String jsonSchema,
            StructuredOutputValidator<T> validator
    ) {
        return of(schemaId, outputType, jsonSchema, validator);
    }

    public OutputSchemaId schemaId() {
        return schemaId;
    }

    public Class<T> outputType() {
        return outputType;
    }

    public String jsonSchema() {
        return jsonSchema;
    }

    Schema compiledSchema() {
        return compiledSchema;
    }

    JsonMapper jsonMapper() {
        return jsonMapper;
    }

    public StructuredOutputValidator<T> validator() {
        return validator;
    }
}
