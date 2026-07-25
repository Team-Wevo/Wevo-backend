package com.wevo.backend.section.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.section.domain.SectionTemplate;
import com.wevo.backend.section.domain.TemplateDependency;
import com.wevo.backend.section.domain.TemplateDependencyType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class TemplateDependencyReferenceDataTest {

    @Test
    void policyEdgesUseDependentToPrerequisiteDirectionAndRequiresType() {
        Map<TemplateDependencyReferenceData.TemplateKey, SectionTemplate> templates =
                TemplateDependencyReferenceData.validateAndIndexTemplates(templates());
        List<TemplateDependency> dependencies =
                TemplateDependencyReferenceData.EDGES.stream()
                        .map(edge -> dependency(
                                templates.get(edge.from()),
                                templates.get(edge.to()),
                                TemplateDependencyType.REQUIRES))
                        .toList();

        TemplateDependencyReferenceData.validateExistingEdges(dependencies, true);

        assertThat(dependencies).hasSize(16);
        assertThat(dependencies).filteredOn(dependency ->
                dependency.getFromTemplate().getResultType() == OutputType.PRESENTATION)
                .hasSize(9);
        assertThat(dependencies).filteredOn(dependency ->
                dependency.getFromTemplate().getResultType() == OutputType.PROPOSAL)
                .hasSize(7);
        assertThat(dependencies).anySatisfy(dependency -> {
            assertThat(dependency.getFromTemplate().getSectionKey()).isEqualTo("target-user");
            assertThat(dependency.getToTemplate().getSectionKey()).isEqualTo("problem-definition");
            assertThat(dependency.getDependencyType()).isEqualTo(TemplateDependencyType.REQUIRES);
        });
    }

    @Test
    void rejectsWrongTypeCrossOutputSelfDependencyAndCycle() {
        List<SectionTemplate> allTemplates = templates();
        Map<String, SectionTemplate> byKey = allTemplates.stream()
                .collect(Collectors.toMap(
                        template -> template.getResultType() + ":" + template.getSectionKey(),
                        Function.identity()));

        assertThatThrownBy(() -> TemplateDependencyReferenceData.validateExistingEdges(
                List.of(dependency(
                        byKey.get("PRESENTATION:target-user"),
                        byKey.get("PRESENTATION:problem-definition"),
                        TemplateDependencyType.BLOCKS)), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REQUIRES");

        assertThatThrownBy(() -> dependency(
                byKey.get("PRESENTATION:target-user"),
                byKey.get("PROPOSAL:proposal-background"),
                TemplateDependencyType.REQUIRES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("결과물 유형");

        SectionTemplate same = byKey.get("PRESENTATION:target-user");
        assertThatThrownBy(() -> dependency(same, same, TemplateDependencyType.REQUIRES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("자기 자신");

        List<TemplateDependency> cycle = List.of(
                dependency(
                        byKey.get("PRESENTATION:target-user"),
                        byKey.get("PRESENTATION:problem-definition"),
                        TemplateDependencyType.REQUIRES),
                dependency(
                        byKey.get("PRESENTATION:problem-definition"),
                        byKey.get("PRESENTATION:target-user"),
                        TemplateDependencyType.REQUIRES));
        assertThatThrownBy(() ->
                TemplateDependencyReferenceData.validateExistingEdges(cycle, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("순환");
    }

    private List<SectionTemplate> templates() {
        List<SectionTemplate> templates = new ArrayList<>();
        add(templates, OutputType.PRESENTATION,
                "problem-definition", "target-user", "solution-direction",
                "core-features", "differentiation", "expected-impact");
        add(templates, OutputType.PROPOSAL,
                "proposal-background", "problem-necessity", "goal-scope",
                "proposal-content", "execution-plan", "expected-impact");
        return templates;
    }

    private void add(List<SectionTemplate> target, OutputType type, String... keys) {
        for (int index = 0; index < keys.length; index++) {
            target.add(SectionTemplate.builder()
                    .resultType(type)
                    .sectionKey(keys[index])
                    .title(keys[index])
                    .orderNo(index + 1)
                    .isRequired(true)
                    .build());
        }
    }

    private TemplateDependency dependency(
            SectionTemplate from,
            SectionTemplate to,
            TemplateDependencyType type
    ) {
        return TemplateDependency.builder()
                .fromTemplate(from)
                .toTemplate(to)
                .dependencyType(type)
                .build();
    }
}
