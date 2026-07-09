package com.wevo.backend.project.service;

import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.project.dto.request.ProjectCreateRequest;
import com.wevo.backend.project.dto.response.ProjectCreateResponse;
import com.wevo.backend.project.repository.ProjectMemberRepository;
import com.wevo.backend.project.repository.ProjectRepository;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.domain.SectionTemplate;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import com.wevo.backend.section.repository.SectionTemplateRepository;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 프로젝트 생성. 생성자를 OWNER 로 등록하고, 결과물 유형에 따라 고정 섹션을 자동 생성한다.
 * (제품 정책서 §2.2, §2.3 — 섹션 추가/삭제/순서변경은 제공하지 않는다.)
 */
@Service
public class ProjectService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectSectionRepository projectSectionRepository;
    private final SectionTemplateRepository sectionTemplateRepository;

    public ProjectService(UserRepository userRepository,
                          ProjectRepository projectRepository,
                          ProjectMemberRepository projectMemberRepository,
                          ProjectSectionRepository projectSectionRepository,
                          SectionTemplateRepository sectionTemplateRepository) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.projectSectionRepository = projectSectionRepository;
        this.sectionTemplateRepository = sectionTemplateRepository;
    }

    @Transactional
    public ProjectCreateResponse create(Long userId, ProjectCreateRequest request) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Project project = projectRepository.save(Project.builder()
                .owner(owner)
                .title(request.title())
                .ideaText(request.ideaText())
                .resultType(request.resultType())
                .audience(request.audience())
                .status(ProjectStatus.ACTIVE)
                .build());

        projectMemberRepository.save(ProjectMember.builder()
                .project(project)
                .user(owner)
                .role(ProjectMemberRole.OWNER)
                .joinedAt(LocalDateTime.now())
                .build());

        List<ProjectSection> sections = createFixedSections(project, request.resultType());
        return ProjectCreateResponse.of(project, sections);
    }

    /**
     * 결과물 유형별 SectionTemplate 을 기준으로 고정 섹션을 생성한다. (초기 상태 COLLECTING)
     */
    private List<ProjectSection> createFixedSections(Project project, OutputType resultType) {
        List<SectionTemplate> templates =
                sectionTemplateRepository.findByResultTypeOrderByOrderNo(resultType);
        if (templates.isEmpty()) {
            // 시더가 baseline 을 넣지 못한 비정상 상태 (운영에선 발생하지 않아야 함)
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        List<ProjectSection> sections = templates.stream()
                .map(template -> ProjectSection.builder()
                        .project(project)
                        .template(template)
                        .title(template.getTitle())
                        .sectionOrder(template.getOrderNo())
                        .status(ProjectSectionStatus.COLLECTING)
                        .needsReReview(false)
                        .build())
                .toList();

        return projectSectionRepository.saveAll(sections);
    }
}
