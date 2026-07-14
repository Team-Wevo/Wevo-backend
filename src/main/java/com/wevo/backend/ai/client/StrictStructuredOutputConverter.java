package com.wevo.backend.ai.client;

import org.springframework.ai.converter.StructuredOutputConverter;
import tools.jackson.databind.JsonNode;

final class StrictStructuredOutputConverter<T> implements StructuredOutputConverter<StructuredConversionResult<T>> {

    private final StructuredOutputDefinition<T> definition;

    StrictStructuredOutputConverter(StructuredOutputDefinition<T> definition) {
        this.definition = definition;
    }

    @Override
    public StructuredConversionResult<T> convert(String text) {
        JsonNode json;
        try {
            json = definition.jsonMapper().readTree(text);
        } catch (RuntimeException exception) {
            return StructuredConversionResult.failure(StructuredConversionFailure.JSON_PARSE);
        }
        if (json == null || !definition.compiledSchema().validate(json).isEmpty()) {
            return StructuredConversionResult.failure(StructuredConversionFailure.SCHEMA_VALIDATION);
        }
        try {
            T value = definition.jsonMapper().treeToValue(json, definition.outputType());
            return value == null
                    ? StructuredConversionResult.failure(StructuredConversionFailure.TYPE_CONVERSION)
                    : StructuredConversionResult.success(value);
        } catch (RuntimeException exception) {
            return StructuredConversionResult.failure(StructuredConversionFailure.TYPE_CONVERSION);
        }
    }

    @Override
    public String getFormat() {
        return "";
    }

    @Override
    public String getJsonSchema() {
        return definition.jsonSchema();
    }
}
