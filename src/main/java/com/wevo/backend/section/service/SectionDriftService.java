package com.wevo.backend.section.service;

import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.review.service.TeamReviewService;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.domain.SectionTemplate;
import com.wevo.backend.section.domain.TemplateDependency;
import com.wevo.backend.section.domain.TemplateDependencyType;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import com.wevo.backend.section.repository.TemplateDependencyRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 확정 이력이 있는 섹션의 실제 본문 변경을 자기 상태 복귀와 직접 하위 드리프트로 전파한다.
 *
 * <p>전이적 후손은 조회하지 않는다. 하위 본문 자체는 바뀌지 않으므로 AI 사전 검토와 synthesis
 * overlay도 유지하며, 검토 단계에 도달한 하위의 팀 검토만 무효화한다.
 */
@Service
public class SectionDriftService {

    private final ProjectAccessGuard projectAccessGuard;
    private final ProjectSectionRepository projectSectionRepository;
    private final TemplateDependencyRepository templateDependencyRepository;
    private final SectionStatusService sectionStatusService;
    private final TeamReviewService teamReviewService;

    public SectionDriftService(
            ProjectAccessGuard projectAccessGuard,
            ProjectSectionRepository projectSectionRepository,
            TemplateDependencyRepository templateDependencyRepository,
            SectionStatusService sectionStatusService,
            TeamReviewService teamReviewService
    ) {
        this.projectAccessGuard = projectAccessGuard;
        this.projectSectionRepository = projectSectionRepository;
        this.templateDependencyRepository = templateDependencyRepository;
        this.sectionStatusService = sectionStatusService;
        this.teamReviewService = teamReviewService;
    }

    /**
     * 새 본문 버전이 append된 뒤 호출한다. 미확정 섹션은 성공적 no-op이다.
     *
     * @return section order, id 순으로 정렬된 직접 하위 영향 목록
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<DriftedSection> propagateConfirmedContentChange(
            ProjectSection changedSection,
            Long actorUserId,
            int contentVersion
    ) {
        validate(changedSection, actorUserId, contentVersion);
        if (changedSection.getConfirmedVersion() == 0) {
            return List.of();
        }
        validateConfirmedSection(changedSection);

        Long projectId = changedSection.getProject().getId();
        ProjectMember actor = projectAccessGuard.requireParticipant(projectId, actorUserId);
        if (changedSection.getStatus() == ProjectSectionStatus.CONFIRMED) {
            sectionStatusService.markReviewingAfterConfirmedContentChange(
                    changedSection, actor, contentVersion);
        } else if (changedSection.getStatus() != ProjectSectionStatus.REVIEWING
                && changedSection.getStatus() != ProjectSectionStatus.DRAFTING) {
            throw new IllegalStateException(
                    "확정 이력이 있는 수정 섹션은 CONFIRMED, REVIEWING 또는 DRAFTING이어야 합니다.");
        }

        List<TemplateDependency> dependencies =
                templateDependencyRepository.findDirectDependents(
                        changedSection.getTemplate().getId(),
                        changedSection.getProject().getResultType(),
                        TemplateDependencyType.REQUIRES);
        if (dependencies.isEmpty()) {
            return List.of();
        }

        List<Long> dependentTemplateIds = dependencies.stream()
                .map(TemplateDependency::getFromTemplate)
                .map(SectionTemplate::getId)
                .toList();
        List<ProjectSection> dependents =
                projectSectionRepository.findDependentsForUpdate(projectId, dependentTemplateIds);
        validateDependents(changedSection, dependentTemplateIds, dependencies.size(), dependents);

        for (ProjectSection dependent : dependents) {
            ProjectSectionStatus status = dependent.getStatus();
            dependent.markDriftReviewRequired();
            if (status == ProjectSectionStatus.CONFIRMED) {
                sectionStatusService.markReviewingAfterPrerequisiteChange(
                        dependent, actor, dependent.getConfirmedVersion());
                teamReviewService.markSectionTeamReviewsOutdated(dependent.getId());
            } else if (status == ProjectSectionStatus.REVIEWING) {
                teamReviewService.markSectionTeamReviewsOutdated(dependent.getId());
            }
        }
        return dependents.stream().map(DriftedSection::from).toList();
    }

    private void validate(ProjectSection section, Long actorUserId, int contentVersion) {
        if (section == null || section.getId() == null || section.getProject() == null
                || section.getProject().getId() == null || actorUserId == null || contentVersion <= 0) {
            throw new IllegalArgumentException("드리프트 전파 입력이 유효하지 않습니다.");
        }
    }

    private void validateConfirmedSection(ProjectSection section) {
        if (section.getTemplate() == null || section.getTemplate().getId() == null) {
            throw new IllegalStateException("확정 section에 template이 없습니다.");
        }
        if (section.getTemplate().getResultType() != section.getProject().getResultType()) {
            throw new IllegalStateException("section과 template의 OutputType이 일치하지 않습니다.");
        }
    }

    private void validateDependents(
            ProjectSection source,
            List<Long> dependentTemplateIds,
            int expectedCount,
            List<ProjectSection> dependents
    ) {
        if (dependents.size() != expectedCount) {
            throw new IllegalStateException("직접 dependency에 대응하는 project section이 누락되었습니다.");
        }
        for (ProjectSection dependent : dependents) {
            if (!source.getProject().getId().equals(dependent.getProject().getId())
                    || dependent.getTemplate() == null
                    || dependent.getTemplate().getResultType() != source.getProject().getResultType()
                    || !dependentTemplateIds.contains(dependent.getTemplate().getId())) {
                throw new IllegalStateException("다른 project 또는 OutputType의 dependency section이 조회되었습니다.");
            }
        }
    }
}
