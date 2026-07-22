package com.wevo.backend.project.service;

import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.project.dto.request.ProjectCreateRequest;
import com.wevo.backend.project.dto.response.ProjectCreateResponse;
import com.wevo.backend.project.dto.response.ProjectDetailResponse;
import com.wevo.backend.project.dto.response.ProjectMemberListResponse;
import com.wevo.backend.project.dto.response.ProjectSummaryResponse;
import com.wevo.backend.project.dto.response.SectionSummaryResponse;
import com.wevo.backend.project.repository.ProjectMemberRepository;
import com.wevo.backend.project.repository.ProjectRepository;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.domain.SectionTemplate;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import com.wevo.backend.section.repository.SectionTemplateRepository;
import com.wevo.backend.section.service.SectionConfirmationSummary;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 프로젝트 생성. 생성자를 OWNER 로 등록하고, 결과물 유형에 따라 고정 섹션을 자동 생성한다.
 * (제품 정책서 §2.2, §2.3 — 섹션 추가/삭제/순서변경은 제공하지 않는다.)
 */
@Service
public class ProjectService {

    /**
     * 제목 미입력 시 서버가 저장하는 기본값 — 응답·목록의 title이 항상 값을 갖도록 보장한다.
     * (API_SPEC §3.2.1 — §2.2 생성 흐름에 이름 입력 단계가 없어 title은 선택이다)
     */
    static final String DEFAULT_TITLE = "제목 없는 프로젝트";

    /** 시간 값은 배포 서버 시간대와 무관하게 KST로 고정한다. (CLAUDE.md §5.4) */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

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

        String title = (request.title() == null || request.title().isBlank())
                ? DEFAULT_TITLE
                : request.title().trim();

        Project project = projectRepository.save(Project.builder()
                .owner(owner)
                .title(title)
                .ideaText(request.ideaText())
                .resultType(request.resultType())
                .audience(request.audience())
                .status(ProjectStatus.ACTIVE)
                .build());

        projectMemberRepository.save(ProjectMember.builder()
                .project(project)
                .user(owner)
                .role(ProjectMemberRole.OWNER)
                .joinedAt(LocalDateTime.now(KST))
                .build());

        List<ProjectSection> sections = createFixedSections(project, request.resultType());
        return ProjectCreateResponse.of(project, sections);
    }

    /**
     * 내가 멤버로 속한 프로젝트 목록을 최신순으로 조회한다.
     */
    @Transactional(readOnly = true)
    public List<ProjectSummaryResponse> getMyProjects(Long userId) {
        return projectMemberRepository.findAllWithProjectByUserId(userId).stream()
                .map(ProjectSummaryResponse::from)
                .toList();
    }

    /**
     * 프로젝트 상세를 조회한다. (멤버만 조회 가능)
     *
     * <p>섹션은 <b>진행도 집계에만</b> 쓰므로 템플릿을 조인하지 않는다 — 핵심 질문·가이드는
     * 이 응답에 담기지 않는다. 템플릿이 필요한 곳은 섹션 목록 조회({@link #getSections})뿐이다.
     */
    @Transactional(readOnly = true)
    public ProjectDetailResponse getProject(Long userId, Long projectId) {
        ProjectMember membership = getMembershipOrThrow(projectId, userId);
        Project project = membership.getProject();

        long memberCount = projectMemberRepository.countByProjectId(projectId);
        List<ProjectSection> sections = projectSectionRepository.findByProjectIdOrderBySectionOrder(projectId);

        return ProjectDetailResponse.of(project, membership.getRole(), memberCount,
                SectionConfirmationSummary.from(sections));
    }

    /**
     * 프로젝트의 섹션 목록을 순서대로 조회한다. (멤버만 조회 가능)
     */
    @Transactional(readOnly = true)
    public List<SectionSummaryResponse> getSections(Long userId, Long projectId) {
        getMembershipOrThrow(projectId, userId);

        return projectSectionRepository.findAllWithTemplateByProjectId(projectId).stream()
                .map(SectionSummaryResponse::from)
                .toList();
    }

    /**
     * 프로젝트 멤버 목록을 조회한다. (멤버만 조회 가능 — API_SPEC §3.2.8)
     *
     * <p>정원이 최대 4명({@link Project#MAX_MEMBERS} — 정책서 §2.1)이라 페이지네이션 없이 전원을
     * 반환한다. MVP 에 회원 탈퇴 기능이 없으므로 사용자 상태로 거르지 않는다.
     */
    @Transactional(readOnly = true)
    public ProjectMemberListResponse getMembers(Long userId, Long projectId) {
        getMembershipOrThrow(projectId, userId);

        return ProjectMemberListResponse.from(
                projectMemberRepository.findAllWithUserByProjectId(projectId));
    }

    /**
     * 프로젝트 멤버십을 확인한다.
     *
     * <p>프로젝트가 없거나 <b>내가 멤버가 아니면 동일하게 {@code PROJECT_NOT_FOUND}(404)</b> 를 던진다.
     * 순번 ID 를 훑어 남의 프로젝트 존재 여부를 알아내지 못하도록 존재 자체를 숨긴다.
     */
    private ProjectMember getMembershipOrThrow(Long projectId, Long userId) {
        return projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
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
                        .build())
                .toList();

        return projectSectionRepository.saveAll(sections);
    }
}
