package com.wevo.backend.project.service;

import com.wevo.backend.project.domain.InviteLink;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.project.dto.request.ProjectCreateRequest;
import com.wevo.backend.project.dto.request.ProjectUpdateRequest;
import com.wevo.backend.project.dto.response.ProjectCreateResponse;
import com.wevo.backend.project.dto.response.ProjectDetailResponse;
import com.wevo.backend.project.dto.response.ProjectMemberListResponse;
import com.wevo.backend.project.dto.response.ProjectSummaryResponse;
import com.wevo.backend.project.dto.response.SectionSummaryResponse;
import com.wevo.backend.project.repository.InviteLinkRepository;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final InviteLinkRepository inviteLinkRepository;

    public ProjectService(UserRepository userRepository,
                          ProjectRepository projectRepository,
                          ProjectMemberRepository projectMemberRepository,
                          ProjectSectionRepository projectSectionRepository,
                          SectionTemplateRepository sectionTemplateRepository,
                          InviteLinkRepository inviteLinkRepository) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.projectSectionRepository = projectSectionRepository;
        this.sectionTemplateRepository = sectionTemplateRepository;
        this.inviteLinkRepository = inviteLinkRepository;
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
     * 내가 멤버로 속한 프로젝트 목록을 <b>최근 작업순</b>으로 조회한다. (API_SPEC §3.2.2)
     *
     * <p>정렬 기준은 프로젝트에 속한 섹션들의 {@code lastActivityAt} 중 <b>최대값</b>이다.
     * {@code projects.updatedAt} 을 쓰지 않는 이유는 그 값이 프로젝트 이름·설명을 고칠 때만
     * 움직여서, 정렬이 사실상 "최근에 이름 바꾼 순"이 되기 때문이다.
     *
     * <p>각 항목에는 <b>마지막 활동 섹션</b>과 확정 진행도를 함께 담는다 — 대시보드 카드가
     * 프로젝트마다 섹션 목록 API 를 다시 부르지 않아도 되게 한다.
     *
     * <p>섹션은 프로젝트별로 나눠 조회하지 않고 <b>한 번에</b> 읽는다. 프로젝트 수만큼 쿼리가
     * 늘면 카드가 많아질수록 목록이 느려진다.
     */
    @Transactional(readOnly = true)
    public List<ProjectSummaryResponse> getMyProjects(Long userId) {
        List<ProjectMember> memberships = projectMemberRepository.findAllWithProjectByUserId(userId);
        if (memberships.isEmpty()) {
            return List.of();
        }

        Map<Long, List<ProjectSection>> sectionsByProject = projectSectionRepository
                .findAllByProjectIdsOrderByLastActivity(memberships.stream()
                        .map(membership -> membership.getProject().getId())
                        .toList())
                .stream()
                .collect(Collectors.groupingBy(section -> section.getProject().getId()));

        Comparator<ProjectMember> byRecentWork = Comparator
                .<ProjectMember, LocalDateTime>comparing(
                        membership -> lastActivityOf(sectionsByProject, membership.getProject().getId()),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                // 활동 시각이 같으면(직후 생성된 프로젝트들) 최근에 만든 것이 위로 온다.
                .thenComparing(membership -> membership.getProject().getCreatedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder()));

        return memberships.stream()
                .sorted(byRecentWork)
                .map(membership -> toSummary(membership, sectionsByProject))
                .toList();
    }

    /**
     * 멤버십 하나를 목록 항목으로 만든다.
     *
     * <p>섹션 목록은 활동 시각 내림차순으로 정렬돼 있으므로 <b>첫 번째가 마지막 활동 섹션</b>이다.
     * 같은 시각이 여러 섹션에 걸리면(프로젝트 생성 직후) 순서가 앞선 섹션이 뽑힌다 — 아직 아무
     * 작업도 없는 프로젝트는 1번 섹션을 가리키는 것이 자연스럽다.
     */
    private ProjectSummaryResponse toSummary(ProjectMember membership,
                                             Map<Long, List<ProjectSection>> sectionsByProject) {
        List<ProjectSection> sections =
                sectionsByProject.getOrDefault(membership.getProject().getId(), List.of());

        return ProjectSummaryResponse.of(
                membership,
                sections.isEmpty() ? null : sections.get(0),
                SectionConfirmationSummary.from(sections));
    }

    /**
     * 정렬 키 — 프로젝트의 마지막 활동 시각.
     *
     * <p>섹션 목록이 활동 시각 내림차순이라 첫 항목의 값이 곧 프로젝트의 최대값이다.
     * 섹션이 없는 비정상 프로젝트는 {@code null} 로 두어 맨 뒤로 보낸다.
     */
    private static LocalDateTime lastActivityOf(Map<Long, List<ProjectSection>> sectionsByProject,
                                                Long projectId) {
        List<ProjectSection> sections = sectionsByProject.getOrDefault(projectId, List.of());
        return sections.isEmpty() ? null : sections.get(0).getLastActivityAt();
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
     * 프로젝트를 보관 처리한다. (OWNER 만 — API_SPEC §3.2.9)
     *
     * <p><b>하드 삭제가 아니다.</b> 프로젝트에는 여러 팀원이 작성한 의견·초안·검토가 매달려 있어
     * 실제 삭제는 남의 결과물까지 되돌릴 수 없게 지운다. 상태만 {@code ARCHIVED} 로 전이시켜
     * 내 프로젝트 목록에서 제외하고, 데이터는 그대로 보존한다.
     *
     * <p>부수 효과로 <b>활성 초대 링크를 모두 비활성화</b>한다 — 보관한 프로젝트에 새 멤버가
     * 합류하는 것을 막는다. 상태 전이와 링크 비활성화는 같은 트랜잭션에서 처리한다.
     *
     * <p>이미 보관된 프로젝트를 다시 삭제하면 아무것도 바꾸지 않고 <b>멱등하게 성공</b>한다
     * (더블 클릭·네트워크 재시도 안전).
     *
     * @throws BusinessException 프로젝트 없음/멤버 아님(존재 숨김, {@code P001}),
     *                           멤버지만 OWNER 아님({@code A002})
     */
    @Transactional
    public void archiveProject(Long userId, Long projectId) {
        ProjectMember membership = getMembershipOrThrow(projectId, userId);
        if (membership.getRole() != ProjectMemberRole.OWNER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Project project = membership.getProject();
        if (project.isArchived()) {
            return;
        }

        project.archive();
        inviteLinkRepository.findAllByProjectIdAndIsActiveTrue(projectId)
                .forEach(InviteLink::deactivate);
    }

    /**
     * 프로젝트의 이름·설명을 수정한다. (OWNER 만 — API_SPEC §3.2.10)
     *
     * <p><b>부분 수정이다.</b> 요청에 없는(= {@code null} 인) 필드는 건드리지 않는다. 이름은 빈 값을
     * 허용하지 않고, 설명은 빈 문자열로 지울 수 있다 — 판정은 {@link Project} 의 상태 변경 메서드가
     * 갖는다.
     *
     * <p><b>AI·섹션 상태에는 영향을 주지 않는다.</b> 이름·설명은 AI 프롬프트 입력이 아니므로
     * {@code synthesisStale} 등 오버레이 플래그를 세우지 않는다. 정책서 §8이 규정한
     * {@code synthesisStale} 트리거는 수집 재오픈과 늦은 GAP 답변뿐이다.
     *
     * <p>보관된({@code ARCHIVED}) 프로젝트도 수정할 수 있다 — 목록에서 빠질 뿐 상세 조회는
     * 계속 동작하므로(§3.2.9) 여기서 따로 막지 않는다.
     *
     * @return 수정 결과를 반영한 상세 응답 (§3.2.3과 동일 구조 — FE 가 재조회하지 않아도 되도록)
     * @throws BusinessException 프로젝트 없음/멤버 아님(존재 숨김, {@code P001}),
     *                           멤버지만 OWNER 아님({@code A002})
     */
    @Transactional
    public ProjectDetailResponse updateProject(Long userId, Long projectId,
                                               ProjectUpdateRequest request) {
        ProjectMember membership = getMembershipOrThrow(projectId, userId);
        if (membership.getRole() != ProjectMemberRole.OWNER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Project project = membership.getProject();
        if (request.title() != null) {
            project.rename(request.title());
        }
        if (request.description() != null) {
            project.changeDescription(request.description());
        }

        long memberCount = projectMemberRepository.countByProjectId(projectId);
        List<ProjectSection> sections = projectSectionRepository.findByProjectIdOrderBySectionOrder(projectId);

        return ProjectDetailResponse.of(project, membership.getRole(), memberCount,
                SectionConfirmationSummary.from(sections));
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
