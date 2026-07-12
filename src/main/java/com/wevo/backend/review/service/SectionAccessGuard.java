package com.wevo.backend.review.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.repository.ProjectMemberRepository;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import org.springframework.stereotype.Component;

/**
 * 섹션 단위 접근 권한 검증 공용 컴포넌트.
 *
 * <p>"섹션 조회 + 팀장(OWNER) 검증"이라는 인가(authorization) 관심사를 한 곳으로 모아,
 * 발급({@link ReviewLinkService})·조회({@link ExternalReviewQueryService}) 등 여러 서비스가 공유한다.
 * (특정 서비스에 의존하지 않도록 별도 컴포넌트로 분리)
 */
@Component
public class SectionAccessGuard {

    private final ProjectSectionRepository projectSectionRepository;
    private final ProjectMemberRepository projectMemberRepository;

    public SectionAccessGuard(ProjectSectionRepository projectSectionRepository,
                              ProjectMemberRepository projectMemberRepository) {
        this.projectSectionRepository = projectSectionRepository;
        this.projectMemberRepository = projectMemberRepository;
    }

    /**
     * 섹션을 조회하고, 요청자가 그 프로젝트의 팀장(OWNER)인지 검증한 뒤 섹션을 반환한다.
     *
     * @throws BusinessException SECTION_NOT_FOUND(섹션 없음) / NOT_PROJECT_MEMBER(멤버 아님) / FORBIDDEN(팀장 아님)
     */
    public ProjectSection requireOwnedSection(Long sectionId, Long userId) {
        ProjectSection section = projectSectionRepository.findById(sectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SECTION_NOT_FOUND));

        ProjectMember member = projectMemberRepository
                .findByProjectIdAndUserId(section.getProject().getId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_PROJECT_MEMBER));
        if (member.getRole() != ProjectMemberRole.OWNER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return section;
    }
}
