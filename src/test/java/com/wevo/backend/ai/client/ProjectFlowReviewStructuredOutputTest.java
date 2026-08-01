package com.wevo.backend.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wevo.backend.ai.domain.ProjectFlowFindingType;
import com.wevo.backend.ai.dto.model.ProjectFlowFindingOutput;
import com.wevo.backend.ai.dto.model.ProjectFlowReviewOutput;
import com.wevo.backend.ai.dto.model.ProjectFlowSectionExcerptOutput;
import com.wevo.backend.ai.service.ProjectFlowReviewOutputDefinition;
import com.wevo.backend.ai.service.ProjectFlowReviewOutputValidator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProjectFlowReviewStructuredOutputTest {
    private final ProjectFlowReviewOutputValidator validator = new ProjectFlowReviewOutputValidator();
    private final StructuredOutputDefinition<ProjectFlowReviewOutput> definition =
            new ProjectFlowReviewOutputDefinition(validator).get();
    private final StrictStructuredOutputConverter<ProjectFlowReviewOutput> converter =
            new StrictStructuredOutputConverter<>(definition);
    private final StructuredOutputValidationContext context =
            StructuredOutputValidationContext.forResourceContents(Map.of(
                    1L, "목표 고객은 대학생이며 예산은 100만원이다.",
                    2L, "직장인을 대상으로 예산 200만원을 사용한다.",
                    3L, "실행 계획을 설명한다."));

    @Test
    void acceptsAllFindingTypesAndEmptySuccess() {
        for (ProjectFlowFindingType type : ProjectFlowFindingType.values()) {
            validator.validate(new ProjectFlowReviewOutput(List.of(finding(type))), context);
        }
        validator.validate(new ProjectFlowReviewOutput(List.of()), context);
    }

    @Test
    void rejectsUnknownSectionWrongSectionExcerptMissingExcerptAndDuplicateOrSingleSection() {
        assertRejected(new ProjectFlowFindingOutput(ProjectFlowFindingType.REDUNDANT_CONTENT,
                List.of(ref(1L, "목표 고객"), ref(9L, "없음")), "설명", "수정"));
        assertRejected(new ProjectFlowFindingOutput(ProjectFlowFindingType.REDUNDANT_CONTENT,
                List.of(ref(1L, "직장인을 대상"), ref(2L, "직장인을 대상")), "설명", "수정"));
        assertRejected(new ProjectFlowFindingOutput(ProjectFlowFindingType.REDUNDANT_CONTENT,
                List.of(ref(1L, "존재하지 않는 문구"), ref(2L, "예산 200만원")), "설명", "수정"));
        assertRejected(new ProjectFlowFindingOutput(ProjectFlowFindingType.REDUNDANT_CONTENT,
                List.of(ref(1L, "목표 고객")), "설명", "수정"));
        ProjectFlowFindingOutput duplicate = finding(ProjectFlowFindingType.REDUNDANT_CONTENT);
        assertThatThrownBy(() -> validator.validate(
                new ProjectFlowReviewOutput(List.of(duplicate, duplicate)), context))
                .isInstanceOf(StructuredOutputSemanticException.class);
    }

    @Test
    void strictSchemaRejectsExtraFieldMissingValueAndUnknownEnum() {
        assertSchemaFailure("""
                {"findings":[{"type":"REDUNDANT_CONTENT","sections":[
                  {"sectionId":1,"targetExcerpt":"목표 고객"},
                  {"sectionId":2,"targetExcerpt":"직장인을 대상"}],
                  "description":"설명","suggestion":"수정","extra":true}]}
                """);
        assertSchemaFailure("""
                {"findings":[{"type":"REDUNDANT_CONTENT","sections":[],"description":"설명"}]}
                """);
        assertSchemaFailure("""
                {"findings":[{"type":"UNKNOWN","sections":[
                  {"sectionId":1,"targetExcerpt":"목표 고객"},
                  {"sectionId":2,"targetExcerpt":"직장인을 대상"}],
                  "description":"설명","suggestion":"수정"}]}
                """);
    }

    private ProjectFlowFindingOutput finding(ProjectFlowFindingType type) {
        return new ProjectFlowFindingOutput(type,
                List.of(ref(1L, "목표 고객"), ref(2L, "직장인을 대상")), "설명", "수정 방향");
    }
    private ProjectFlowSectionExcerptOutput ref(Long id, String excerpt) {
        return new ProjectFlowSectionExcerptOutput(id, excerpt);
    }
    private void assertRejected(ProjectFlowFindingOutput finding) {
        assertThatThrownBy(() -> validator.validate(new ProjectFlowReviewOutput(List.of(finding)), context))
                .isInstanceOf(StructuredOutputSemanticException.class);
    }
    private void assertSchemaFailure(String json) {
        assertThat(converter.convert(json).failure()).isEqualTo(StructuredConversionFailure.SCHEMA_VALIDATION);
    }
}
