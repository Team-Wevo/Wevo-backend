package com.wevo.backend.ai.client;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredOutputDefinitionTest {

    private final StructuredOutputDefinition<TestOutput> definition = StructuredOutputDefinition.of(
            new OutputSchemaId("test-output", 1),
            TestOutput.class,
            (output, context) -> context.requireAllowedResourceId(output.resourceId())
    );
    private final StrictStructuredOutputConverter<TestOutput> converter =
            new StrictStructuredOutputConverter<>(definition);

    @Test
    void convertsValidJsonToRecord() {
        StructuredConversionResult<TestOutput> result = converter.convert(
                "{\"resourceId\":7,\"signal\":\"CLEAR\"}"
        );

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo(new TestOutput(7L, TestSignal.CLEAR));
        assertThat(definition.schemaId().trackingValue()).isEqualTo("test-output:v1");
        assertThat(definition.jsonSchema()).contains("additionalProperties", "false");
    }

    @Test
    void rejectsMissingExtraAndWrongEnumFieldsAtSchemaBoundary() {
        assertSchemaFailure("{\"resourceId\":7}");
        assertSchemaFailure("{\"resourceId\":7,\"signal\":\"CLEAR\",\"extra\":true}");
        assertSchemaFailure("{\"resourceId\":7,\"signal\":\"clear\"}");
        assertSchemaFailure("{\"resourceId\":7,\"signal\":\"UNKNOWN\"}");
    }

    @Test
    void distinguishesMalformedJson() {
        StructuredConversionResult<TestOutput> result = converter.convert("not-json");

        assertThat(result.failure()).isEqualTo(StructuredConversionFailure.JSON_PARSE);
    }

    @Test
    void rejectsInvalidSchemaAndNonRecordOutputType() {
        assertThatThrownBy(() -> StructuredOutputDefinition.withSchema(
                new OutputSchemaId("bad-schema", 1),
                TestOutput.class,
                "not-json",
                StructuredOutputValidator.noOp()
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> StructuredOutputDefinition.of(
                new OutputSchemaId("bad-type", 1),
                String.class
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("record");
    }

    @Test
    void semanticValidatorRejectsResourceOutsideAllowlist() {
        TestOutput output = new TestOutput(7L, TestSignal.CLEAR);

        assertThatThrownBy(() -> definition.validator().validate(
                output,
                new StructuredOutputValidationContext(Set.of(8L))
        )).isInstanceOf(StructuredOutputSemanticException.class);
    }

    private void assertSchemaFailure(String json) {
        StructuredConversionResult<TestOutput> result = converter.convert(json);
        assertThat(result.failure()).isEqualTo(StructuredConversionFailure.SCHEMA_VALIDATION);
    }

    private record TestOutput(Long resourceId, TestSignal signal) {
    }

    private enum TestSignal {
        CLEAR,
        PARTIAL,
        UNCLEAR
    }
}
