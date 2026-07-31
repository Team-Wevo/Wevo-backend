package com.wevo.backend.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wevo.backend.ai.dto.model.AuthorIntentExtractionOutput;
import com.wevo.backend.ai.service.AuthorIntentExtractionOutputDefinition;
import com.wevo.backend.ai.service.AuthorIntentExtractionOutputValidator;
import com.wevo.backend.section.domain.AuthorIntentTextPolicy;
import org.junit.jupiter.api.Test;

class AuthorIntentExtractionOutputDefinitionTest {

    private final AuthorIntentExtractionOutputValidator validator =
            new AuthorIntentExtractionOutputValidator();
    private final StructuredOutputDefinition<AuthorIntentExtractionOutput> definition =
            new AuthorIntentExtractionOutputDefinition(validator).get();
    private final StrictStructuredOutputConverter<AuthorIntentExtractionOutput> converter =
            new StrictStructuredOutputConverter<>(definition);

    @Test
    void acceptsExactlyOneValidIntentField() {
        StructuredConversionResult<AuthorIntentExtractionOutput> result = converter.convert("""
                {"intent":"흩어진 팀 의견을 하나의 실행 가능한 제안으로 정리하는 것이 핵심이다."}
                """);

        assertThat(result.isSuccess()).isTrue();
        assertThat(definition.schemaId().trackingValue()).isEqualTo("author-intent-output:v1");
    }

    @Test
    void rejectsMissingNullBlankNewlineOversizeAndExtraFieldAtSchemaBoundary() {
        assertFailure("{}");
        assertFailure("{\"intent\":null}");
        assertFailure("{\"intent\":\"   \"}");
        assertFailure("{\"intent\":\"첫 문장\\n둘째 문장\"}");
        assertFailure("{\"intent\":\"%s\"}".formatted(
                "가".repeat(AuthorIntentTextPolicy.MAX_LENGTH + 1)));
        assertFailure("{\"intent\":\"유효한 문장이다.\",\"extra\":true}");
    }

    @Test
    void semanticValidatorRejectsListFormattingEvenWhenSchemaShapeIsValid() {
        assertThatThrownBy(() -> validator.validate(
                new AuthorIntentExtractionOutput("- 핵심 의도입니다."),
                StructuredOutputValidationContext.empty()))
                .isInstanceOf(StructuredOutputSemanticException.class);
    }

    private void assertFailure(String json) {
        assertThat(converter.convert(json).isSuccess()).isFalse();
    }
}
