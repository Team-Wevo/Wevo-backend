package com.wevo.backend.section.service;

import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** precheck 결과 반영·rewrite 적용 시 자기 section과 직접 상위를 안정된 순서로 잠근다. */
@Service
public class SectionPrecheckStateService {

    private final TemplateDependencyQueryService dependencyQueryService;
    private final ProjectSectionRepository projectSectionRepository;

    public SectionPrecheckStateService(
            TemplateDependencyQueryService dependencyQueryService,
            ProjectSectionRepository projectSectionRepository
    ) {
        this.dependencyQueryService = dependencyQueryService;
        this.projectSectionRepository = projectSectionRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockInputSections(VerifiedProjectAccess access, Long sectionId) {
        List<Long> ids = new ArrayList<>();
        ids.add(sectionId);
        dependencyQueryService.findDirectPrerequisites(access, sectionId).stream()
                .map(SectionDependencyReference::sectionId)
                .forEach(ids::add);
        ids.sort(Comparator.naturalOrder());
        List<?> locked = projectSectionRepository.findAllByIdInOrderByIdForUpdate(ids);
        if (locked.size() != ids.size()) {
            throw new IllegalStateException("사전 검토 입력 section 잠금 대상을 모두 찾을 수 없습니다.");
        }
    }
}
