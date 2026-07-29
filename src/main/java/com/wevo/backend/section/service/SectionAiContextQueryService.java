package com.wevo.backend.section.service;

import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.domain.SectionDraft;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import com.wevo.backend.section.repository.SectionDraftRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** section metadata와 최신본·확정본을 명시적으로 구분하는 AI 입력용 조회 경계. */
@Service
@Transactional(readOnly = true)
public class SectionAiContextQueryService {

    private final ProjectSectionRepository projectSectionRepository;
    private final SectionDraftRepository sectionDraftRepository;
    private final TemplateDependencyQueryService dependencyQueryService;

    public SectionAiContextQueryService(
            ProjectSectionRepository projectSectionRepository,
            SectionDraftRepository sectionDraftRepository,
            TemplateDependencyQueryService dependencyQueryService
    ) {
        this.projectSectionRepository = projectSectionRepository;
        this.sectionDraftRepository = sectionDraftRepository;
        this.dependencyQueryService = dependencyQueryService;
    }

    public SectionAiMetadata getMetadata(VerifiedProjectAccess access, Long sectionId) {
        ProjectSection section = dependencyQueryService.requireTarget(access, sectionId);
        return new SectionAiMetadata(
                section.getId(),
                section.getProject().getId(),
                section.getTitle(),
                section.getSectionOrder(),
                section.getStatus(),
                section.getOpinionGateGeneration(),
                section.isSynthesisStale(),
                section.getTemplate().getSectionKey(),
                normalizeOptional(section.getTemplate().getDescription()),
                normalizeOptional(section.getTemplate().getGuideText()));
    }

    public SectionVersionedContent getLatestDraft(VerifiedProjectAccess access, Long sectionId) {
        dependencyQueryService.requireTarget(access, sectionId);
        SectionDraft draft = sectionDraftRepository
                .findTopByProjectSection_IdOrderByVersionDesc(sectionId)
                .orElseThrow(() -> new IllegalStateException("AI context에 필요한 최신 draft가 없습니다."));
        return toVersionedContent(draft, sectionId);
    }

    /** 초안 생성 snapshot용 최신 본문. 최초 생성이면 version 0과 null 본문을 반환한다. */
    public SectionVersionedContent getLatestDraftOrEmpty(
            VerifiedProjectAccess access,
            Long sectionId
    ) {
        dependencyQueryService.requireTarget(access, sectionId);
        return sectionDraftRepository.findTopByProjectSection_IdOrderByVersionDesc(sectionId)
                .map(draft -> toVersionedContent(draft, sectionId))
                .orElseGet(() -> new SectionVersionedContent(sectionId, 0, null));
    }

    public SectionVersionedContent getConfirmedDraft(VerifiedProjectAccess access, Long sectionId) {
        ProjectSection section = dependencyQueryService.requireTarget(access, sectionId);
        return confirmedContent(section);
    }

    /** synthesis/draft generation용 직접 상위 확정본. */
    public List<PrerequisiteSectionContent> findDirectConfirmedPrerequisites(
            VerifiedProjectAccess access, Long sectionId
    ) {
        return dependencyQueryService.findDirectPrerequisites(access, sectionId).stream()
                .map(reference -> {
                    ProjectSection section = requireReferencedSection(access, reference);
                    SectionVersionedContent content = confirmedContent(section);
                    return prerequisite(reference, content);
                })
                .toList();
    }

    /** precheck용 직접 상위 최신본. */
    public List<PrerequisiteSectionContent> findDirectLatestPrerequisites(
            VerifiedProjectAccess access, Long sectionId
    ) {
        return dependencyQueryService.findDirectPrerequisites(access, sectionId).stream()
                .map(reference -> {
                    ProjectSection section = requireReferencedSection(access, reference);
                    SectionDraft draft = sectionDraftRepository
                            .findTopByProjectSection_IdOrderByVersionDesc(section.getId())
                            .orElseThrow(() -> new IllegalStateException(
                                    "AI context에 필요한 직접 상위 최신 draft가 없습니다."));
                    return prerequisite(reference, toVersionedContent(draft, section.getId()));
                })
                .toList();
    }

    private ProjectSection requireReferencedSection(
            VerifiedProjectAccess access, SectionDependencyReference reference
    ) {
        ProjectSection section = projectSectionRepository.findByIdWithTemplate(reference.sectionId())
                .orElseThrow(() -> new IllegalStateException("dependency section이 존재하지 않습니다."));
        if (!access.projectId().equals(section.getProject().getId())
                || section.getTemplate() == null
                || !reference.templateKey().equals(section.getTemplate().getSectionKey())) {
            throw new IllegalStateException("dependency section의 소속 또는 template이 일치하지 않습니다.");
        }
        return section;
    }

    private SectionVersionedContent confirmedContent(ProjectSection section) {
        if (section.getStatus() != ProjectSectionStatus.CONFIRMED || section.getConfirmedVersion() <= 0) {
            throw new IllegalStateException("AI context에 필요한 직접 상위 section이 확정 상태가 아닙니다.");
        }
        SectionDraft draft = sectionDraftRepository
                .findByProjectSection_IdAndVersion(section.getId(), section.getConfirmedVersion())
                .orElseThrow(() -> new IllegalStateException(
                        "section의 confirmedVersion에 대응하는 draft가 없습니다."));
        return toVersionedContent(draft, section.getId());
    }

    private SectionVersionedContent toVersionedContent(SectionDraft draft, Long expectedSectionId) {
        if (!expectedSectionId.equals(draft.getProjectSection().getId())
                || draft.getVersion() == null
                || draft.getVersion() <= 0
                || !StringUtils.hasText(draft.getContent())) {
            throw new IllegalStateException("AI context draft 데이터가 유효하지 않습니다.");
        }
        return new SectionVersionedContent(expectedSectionId, draft.getVersion(), draft.getContent());
    }

    private PrerequisiteSectionContent prerequisite(
            SectionDependencyReference reference, SectionVersionedContent content
    ) {
        return new PrerequisiteSectionContent(
                reference.sectionId(),
                reference.templateKey(),
                reference.sectionOrder(),
                content.contentVersion(),
                content.content());
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
