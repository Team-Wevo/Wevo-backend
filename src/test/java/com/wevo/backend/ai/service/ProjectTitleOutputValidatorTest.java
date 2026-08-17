package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wevo.backend.ai.client.StructuredOutputSemanticException;
import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.dto.model.ProjectTitleSuggestionOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectTitleOutputValidatorTest {

    private final ProjectTitleOutputValidator validator = new ProjectTitleOutputValidator();
    private final StructuredOutputValidationContext context = StructuredOutputValidationContext.empty();

    @Test
    @DisplayName("한 줄·길이 이내의 제목은 통과한다")
    void validTitle_passes() {
        assertThatCode(() -> validator.validate(
                new ProjectTitleSuggestionOutput("교내 해커톤 발표 구성안"), context))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("공백만 있는 제목은 거부한다")
    void blankTitle_throws() {
        assertThatThrownBy(() -> validator.validate(
                new ProjectTitleSuggestionOutput("   "), context))
                .isInstanceOf(StructuredOutputSemanticException.class);
    }

    @Test
    @DisplayName("개행이 든 제목은 거부한다")
    void multilineTitle_throws() {
        assertThatThrownBy(() -> validator.validate(
                new ProjectTitleSuggestionOutput("첫 줄\n둘째 줄"), context))
                .isInstanceOf(StructuredOutputSemanticException.class);
    }

    @Test
    @DisplayName("최대 길이를 초과한 제목은 거부한다")
    void tooLongTitle_throws() {
        String tooLong = "가".repeat(ProjectTitleContract.MAX_TITLE_LENGTH + 1);
        assertThatThrownBy(() -> validator.validate(
                new ProjectTitleSuggestionOutput(tooLong), context))
                .isInstanceOf(StructuredOutputSemanticException.class);
    }

    @Test
    @DisplayName("output 이 null 이면 거부한다")
    void nullOutput_throws() {
        assertThatThrownBy(() -> validator.validate(null, context))
                .isInstanceOf(StructuredOutputSemanticException.class);
    }
}
