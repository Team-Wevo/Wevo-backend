package com.wevo.backend.project.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import org.springframework.stereotype.Component;

/**
 * 섹션 조회와 프로젝트 역할 검사를 결합하는 공통 인가 컴포넌트.
 *
 * <p>섹션 기반 API가 도메인별로 같은 멤버십 조회를 반복하지 않도록 project 도메인에서
 * 공통 진입점을 제공한다.
 *
 * <p>모든 검사는 섹션 존재 여부를 먼저 확인한 뒤, 섹션이 속한 프로젝트 ID로
 * {@link ProjectAccessGuard}의 역할 검사를 수행한다. 따라서 존재하지 않는 섹션은 역할 조회 없이
 * {@link ErrorCode#SECTION_NOT_FOUND}로 처리된다.
 */
@Component
public class SectionAccessGuard {

    private final ProjectSectionRepository projectSectionRepository;
    private final ProjectAccessGuard projectAccessGuard;

    public SectionAccessGuard(ProjectSectionRepository projectSectionRepository,
                              ProjectAccessGuard projectAccessGuard) {
        this.projectSectionRepository = projectSectionRepository;
        this.projectAccessGuard = projectAccessGuard;
    }

    /**
     * 섹션을 조회하고 요청자가 프로젝트 참여자인지 검증한다.
     *
     * @return 접근 권한이 확인된 섹션
     * @throws BusinessException 섹션이 없으면 {@link ErrorCode#SECTION_NOT_FOUND}, 프로젝트에
     *                           참여하지 않았으면 {@link ErrorCode#NOT_PROJECT_MEMBER}
     */
    public ProjectSection requireParticipantSection(Long sectionId, Long userId) {
        ProjectSection section = requireSection(sectionId);
        projectAccessGuard.requireParticipant(section.getProject().getId(), userId);
        return section;
    }

    /**
     * 섹션을 조회하고 요청자가 프로젝트 팀장(OWNER)인지 검증한다.
     *
     * @return OWNER 접근 권한이 확인된 섹션
     * @throws BusinessException 섹션이 없으면 {@link ErrorCode#SECTION_NOT_FOUND}, 프로젝트에
     *                           참여하지 않았으면 {@link ErrorCode#NOT_PROJECT_MEMBER},
     *                           MEMBER이면 {@link ErrorCode#FORBIDDEN}
     */
    public ProjectSection requireOwnedSection(Long sectionId, Long userId) {
        ProjectSection section = requireSection(sectionId);
        projectAccessGuard.requireOwner(section.getProject().getId(), userId);
        return section;
    }

    /**
     * 섹션을 조회하고 요청자가 프로젝트 팀원(MEMBER)인지 검증한다. OWNER는 통과하지 않는다.
     *
     * @return MEMBER 접근 권한이 확인된 섹션
     * @throws BusinessException 섹션이 없으면 {@link ErrorCode#SECTION_NOT_FOUND}, 프로젝트에
     *                           참여하지 않았으면 {@link ErrorCode#NOT_PROJECT_MEMBER},
     *                           OWNER이면 {@link ErrorCode#FORBIDDEN}
     */
    public ProjectSection requireMemberSection(Long sectionId, Long userId) {
        ProjectSection section = requireSection(sectionId);
        projectAccessGuard.requireMember(section.getProject().getId(), userId);
        return section;
    }

    /**
     * 역할 검사에 앞서 대상 섹션을 조회한다.
     */
    private ProjectSection requireSection(Long sectionId) {
        return projectSectionRepository.findById(sectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SECTION_NOT_FOUND));
    }
}
