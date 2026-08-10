package com.wevo.backend.section.service;

import com.wevo.backend.project.service.ProjectOutputQueryService;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.section.domain.TemplateDependencyType;
import com.wevo.backend.section.repository.TemplateDependencyRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * section 도메인이 공개하는 프로젝트 유형별 직접 의존 관계 조회 경계.
 *
 * <p>결과물 유형은 프로젝트 엔티티에서 직접 꺼내지 않고 project 도메인이 공개한
 * {@link ProjectOutputQueryService} 의 값 객체로 받는다. (CLAUDE.md §6)
 */
@Service
@Transactional(readOnly = true)
public class SectionDependencyQueryService {

    private final TemplateDependencyRepository repository;
    private final ProjectOutputQueryService projectOutputQueryService;

    public SectionDependencyQueryService(TemplateDependencyRepository repository,
                                         ProjectOutputQueryService projectOutputQueryService) {
        this.repository = repository;
        this.projectOutputQueryService = projectOutputQueryService;
    }

    public List<SectionDependencyEdge> findRequiresEdges(VerifiedProjectAccess access) {
        var outputType = projectOutputQueryService.getOutputHeader(access).resultType();
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
