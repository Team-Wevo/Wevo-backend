package com.wevo.backend.section.service;

import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.SectionTemplate;
import com.wevo.backend.section.domain.TemplateDependency;
import com.wevo.backend.section.domain.TemplateDependencyType;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import com.wevo.backend.section.repository.TemplateDependencyRepository;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제품 정책의 직접 {@code dependsOn} 상·하위 section 조회 경계.
 *
 * <p>{@code from = 하위}, {@code to = 상위}, {@code type = REQUIRES}만 사용한다.
 */
@Service
@Transactional(readOnly = true)
public class TemplateDependencyQueryService {

    private final ProjectSectionRepository projectSectionRepository;
    private final TemplateDependencyRepository templateDependencyRepository;

    public TemplateDependencyQueryService(
            ProjectSectionRepository projectSectionRepository,
            TemplateDependencyRepository templateDependencyRepository
    ) {
        this.projectSectionRepository = projectSectionRepository;
        this.templateDependencyRepository = templateDependencyRepository;
    }

    public List<SectionDependencyReference> findDirectPrerequisites(
            VerifiedProjectAccess access, Long sectionId
    ) {
        ProjectSection target = requireTarget(access, sectionId);
        List<TemplateDependency> dependencies = templateDependencyRepository.findDirectPrerequisites(
                target.getTemplate().getId(),
                target.getProject().getResultType(),
                TemplateDependencyType.REQUIRES);
        return resolveSections(access, dependencies, dependency -> dependency.getToTemplate());
    }

    /** AI-11 변경 전파가 재사용하는 직접 하위 조회. */
    public List<SectionDependencyReference> findDirectDependents(
            VerifiedProjectAccess access, Long sectionId
    ) {
        ProjectSection target = requireTarget(access, sectionId);
        List<TemplateDependency> dependencies = templateDependencyRepository.findDirectDependents(
                target.getTemplate().getId(),
                target.getProject().getResultType(),
                TemplateDependencyType.REQUIRES);
        return resolveSections(access, dependencies, dependency -> dependency.getFromTemplate());
    }

    ProjectSection requireTarget(VerifiedProjectAccess access, Long sectionId) {
        if (access == null) {
            throw new IllegalArgumentException("검증된 프로젝트 접근 정보는 필수입니다.");
        }
        ProjectSection section = projectSectionRepository.findByIdWithTemplate(sectionId)
                .orElseThrow(() -> new IllegalStateException("AI context 대상 section이 존재하지 않습니다."));
        if (!access.projectId().equals(section.getProject().getId())) {
            throw new IllegalStateException("검증된 project와 대상 section의 소속이 일치하지 않습니다.");
        }
        if (section.getTemplate() == null) {
            throw new IllegalStateException("AI context 대상 section에 template이 없습니다.");
        }
        OutputType outputType = section.getProject().getResultType();
        if (section.getTemplate().getResultType() != outputType) {
            throw new IllegalStateException("section과 template의 OutputType이 일치하지 않습니다.");
        }
        return section;
    }

    private List<SectionDependencyReference> resolveSections(
            VerifiedProjectAccess access,
            List<TemplateDependency> dependencies,
            Function<TemplateDependency, SectionTemplate> templateSelector
    ) {
        List<Long> templateIds = dependencies.stream()
                .map(templateSelector)
                .map(SectionTemplate::getId)
                .toList();
        if (templateIds.isEmpty()) {
            return List.of();
        }

        List<ProjectSection> sections =
                projectSectionRepository.findByProject_IdAndTemplate_IdInOrderBySectionOrderAscIdAsc(
                        access.projectId(), templateIds);
        if (sections.size() != dependencies.size()) {
            throw new IllegalStateException("직접 dependency에 대응하는 project section이 누락되었습니다.");
        }
        for (ProjectSection section : sections) {
            if (!access.projectId().equals(section.getProject().getId())
                    || section.getTemplate() == null
                    || section.getTemplate().getResultType() != section.getProject().getResultType()
                    || !templateIds.contains(section.getTemplate().getId())) {
                throw new IllegalStateException("다른 project 또는 OutputType의 dependency section이 조회되었습니다.");
            }
        }
        return sections.stream()
                .map(section -> new SectionDependencyReference(
                        section.getId(),
                        section.getTemplate().getSectionKey(),
                        section.getSectionOrder()))
                .toList();
    }
}
