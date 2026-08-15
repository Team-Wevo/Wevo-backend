package com.wevo.backend.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.wevo.backend.ai.dto.model.ProjectTitleSuggestionOutput;
import com.wevo.backend.ai.service.ProjectTitleOutputDefinition;
import com.wevo.backend.ai.service.ProjectTitleOutputValidator;
import org.junit.jupiter.api.Test;

/**
 * 프로젝트 제목 strict schema 가 선행·후행 공백과 개행을 스키마 경계에서 거부하는지 고정한다. (#316)
 *
 * <p>{@code pattern} 이 느슨하면 {@code " 제목 "} 처럼 앞뒤 공백이 붙은 제목이 통과한다. 저장부가
 * {@code strip()} 으로 다듬긴 하지만, 계약을 경계에서 강제해 AI 가 애초에 공백을 붙이지 못하게 한다.
 */
class ProjectTitleStructuredOutputTest {

    private final StructuredOutputDefinition<ProjectTitleSuggestionOutput> definition =
            new ProjectTitleOutputDefinition(new ProjectTitleOutputValidator()).get();
    private final StrictStructuredOutputConverter<ProjectTitleSuggestionOutput> converter =
            new StrictStructuredOutputConverter<>(definition);

    @Test
    void acceptsTrimmedTitle() {
        assertThat(converter.convert("{\"title\":\"위보 기획서\"}").isSuccess()).isTrue();
    }

    @Test
    void acceptsSingleNonSpaceCharacter() {
        assertThat(converter.convert("{\"title\":\"위\"}").isSuccess()).isTrue();
    }

    @Test
    void rejectsLeadingOrTrailingWhitespaceAsSchemaFailure() {
        assertSchemaFailure("{\"title\":\" 위보 기획서\"}");
        assertSchemaFailure("{\"title\":\"위보 기획서 \"}");
        assertSchemaFailure("{\"title\":\" 위보 기획서 \"}");
    }

    @Test
    void rejectsNewlineAsSchemaFailure() {
        assertSchemaFailure("{\"title\":\"위보\\n기획서\"}");
    }

    private void assertSchemaFailure(String json) {
        assertThat(converter.convert(json).failure())
                .isEqualTo(StructuredConversionFailure.SCHEMA_VALIDATION);
    }
}
