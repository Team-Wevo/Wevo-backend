package com.wevo.backend.section.seed;

import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.section.domain.SectionTemplate;
import com.wevo.backend.section.domain.TemplateDependency;
import com.wevo.backend.section.domain.TemplateDependencyType;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 제품 정책 §6.4의 직접 {@code dependsOn} 기준 데이터와 검증 규칙.
 *
 * <p>edge 방향은 {@code dependentKey(from) -> prerequisiteKey(to)}이며 모든 기준 edge는
 * {@link TemplateDependencyType#REQUIRES}다.
 */
final class TemplateDependencyReferenceData {

    static final List<Edge> EDGES = List.of(
            edge(OutputType.PRESENTATION, "target-user", "problem-definition"),
            edge(OutputType.PRESENTATION, "solution-direction", "problem-definition"),
            edge(OutputType.PRESENTATION, "solution-direction", "target-user"),
            edge(OutputType.PRESENTATION, "core-features", "solution-direction"),
            edge(OutputType.PRESENTATION, "differentiation", "solution-direction"),
            edge(OutputType.PRESENTATION, "differentiation", "core-features"),
            edge(OutputType.PRESENTATION, "expected-impact", "problem-definition"),
            edge(OutputType.PRESENTATION, "expected-impact", "solution-direction"),
            edge(OutputType.PRESENTATION, "expected-impact", "core-features"),
            edge(OutputType.PROPOSAL, "problem-necessity", "proposal-background"),
            edge(OutputType.PROPOSAL, "goal-scope", "problem-necessity"),
            edge(OutputType.PROPOSAL, "proposal-content", "goal-scope"),
            edge(OutputType.PROPOSAL, "execution-plan", "proposal-content"),
            edge(OutputType.PROPOSAL, "expected-impact", "problem-necessity"),
            edge(OutputType.PROPOSAL, "expected-impact", "proposal-content"),
            edge(OutputType.PROPOSAL, "expected-impact", "execution-plan")
    );

    private TemplateDependencyReferenceData() {
    }

    static Map<TemplateKey, SectionTemplate> validateAndIndexTemplates(List<SectionTemplate> templates) {
        Map<TemplateKey, SectionTemplate> indexed = new HashMap<>();
        for (SectionTemplate template : templates) {
            TemplateKey key = new TemplateKey(template.getResultType(), template.getSectionKey());
            if (indexed.put(key, template) != null) {
                throw invalid("중복된 section template key가 있습니다.");
            }
        }

        Set<TemplateKey> expectedKeys = new HashSet<>();
        EDGES.forEach(edge -> {
            expectedKeys.add(edge.from());
            expectedKeys.add(edge.to());
        });
        if (!indexed.keySet().equals(expectedKeys)) {
            throw invalid("section template 기준 데이터가 제품 정책의 12개 템플릿과 일치하지 않습니다.");
        }
        return Map.copyOf(indexed);
    }

    static void validateExistingEdges(List<TemplateDependency> dependencies, boolean requireComplete) {
        Set<Edge> actual = new HashSet<>();
        Set<Edge> unexpected = new HashSet<>();
        Map<TemplateKey, Set<TemplateKey>> graph = new HashMap<>();

        for (TemplateDependency dependency : dependencies) {
            SectionTemplate from = dependency.getFromTemplate();
            SectionTemplate to = dependency.getToTemplate();
            if (from.getResultType() != to.getResultType()) {
                throw invalid("서로 다른 OutputType의 dependency가 있습니다.");
            }
            if (from.getId() != null && from.getId().equals(to.getId())) {
                throw invalid("자기 의존 dependency가 있습니다.");
            }
            if (dependency.getDependencyType() != TemplateDependencyType.REQUIRES) {
                throw invalid("제품 dependsOn은 REQUIRES type이어야 합니다.");
            }

            Edge edge = edge(from.getResultType(), from.getSectionKey(), to.getSectionKey());
            if (edge.from().equals(edge.to())) {
                throw invalid("자기 의존 dependency가 있습니다.");
            }
            if (!actual.add(edge)) {
                throw invalid("중복된 dependency가 있습니다.");
            }
            if (!EDGES.contains(edge)) {
                unexpected.add(edge);
            }
            graph.computeIfAbsent(edge.from(), ignored -> new HashSet<>()).add(edge.to());
        }

        validateAcyclic(graph);
        if (!unexpected.isEmpty()) {
            throw invalid("제품 정책에 없거나 방향이 뒤집힌 dependency가 있습니다.");
        }
        if (requireComplete && (!actual.equals(Set.copyOf(EDGES)) || dependencies.size() != EDGES.size())) {
            throw invalid("dependency 기준 데이터가 제품 정책의 16개 관계와 일치하지 않습니다.");
        }
    }

    private static void validateAcyclic(Map<TemplateKey, Set<TemplateKey>> graph) {
        Set<TemplateKey> visited = new HashSet<>();
        Set<TemplateKey> visiting = new HashSet<>();
        for (TemplateKey node : graph.keySet()) {
            if (!visited.contains(node)) {
                visit(node, graph, visiting, visited);
            }
        }
    }

    private static void visit(
            TemplateKey start,
            Map<TemplateKey, Set<TemplateKey>> graph,
            Set<TemplateKey> visiting,
            Set<TemplateKey> visited
    ) {
        ArrayDeque<Visit> stack = new ArrayDeque<>();
        stack.push(new Visit(start, false));
        while (!stack.isEmpty()) {
            Visit current = stack.pop();
            if (current.leaving()) {
                visiting.remove(current.key());
                visited.add(current.key());
                continue;
            }
            if (visited.contains(current.key())) {
                continue;
            }
            if (!visiting.add(current.key())) {
                throw invalid("dependency 기준 데이터에 순환이 있습니다.");
            }
            stack.push(new Visit(current.key(), true));
            for (TemplateKey next : graph.getOrDefault(current.key(), Set.of())) {
                if (visiting.contains(next)) {
                    throw invalid("dependency 기준 데이터에 순환이 있습니다.");
                }
                if (!visited.contains(next)) {
                    stack.push(new Visit(next, false));
                }
            }
        }
    }

    private static Edge edge(OutputType resultType, String fromKey, String toKey) {
        return new Edge(new TemplateKey(resultType, fromKey), new TemplateKey(resultType, toKey));
    }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException("section reference data 검증 실패: " + message);
    }

    record TemplateKey(OutputType resultType, String sectionKey) {
    }

    record Edge(TemplateKey from, TemplateKey to) {
    }

    private record Visit(TemplateKey key, boolean leaving) {
    }
}
