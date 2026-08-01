package com.wevo.backend.section.service;

import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.section.domain.TemplateDependencyType;
import com.wevo.backend.section.repository.TemplateDependencyRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** section 도메인이 공개하는 프로젝트 유형별 직접 의존 관계 조회 경계. */
@Service
@Transactional(readOnly = true)
public class SectionDependencyQueryService {

    private final TemplateDependencyRepository repository;

    public SectionDependencyQueryService(TemplateDependencyRepository repository) {
        this.repository = repository;
    }

    public List<SectionDependencyEdge> findRequiresEdges(VerifiedProjectAccess access) {
        var outputType = access.project().getResultType();
        return repository.findAllWithTemplates().stream()
                .filter(edge -> edge.getDependencyType() == TemplateDependencyType.REQUIRES)
                .filter(edge -> edge.getFromTemplate().getResultType() == outputType)
                .map(edge -> new SectionDependencyEdge(
                        edge.getFromTemplate().getSectionKey(),
                        edge.getToTemplate().getSectionKey()))
                .sorted(Comparator.comparing(SectionDependencyEdge::fromTemplateKey)
                        .thenComparing(SectionDependencyEdge::toTemplateKey))
                .toList();
    }
}
