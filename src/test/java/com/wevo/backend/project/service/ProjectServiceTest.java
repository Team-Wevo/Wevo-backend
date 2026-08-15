package com.wevo.backend.project.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
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
import com.wevo.backend.project.domain.InviteLink;
import com.wevo.backend.project.repository.InviteLinkRepository;
import com.wevo.backend.project.repository.ProjectMemberRepository;
import com.wevo.backend.project.repository.ProjectRepository;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.domain.SectionTemplate;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import com.wevo.backend.section.repository.SectionTemplateRepository;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import com.wevo.backend.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private ProjectSectionRepository projectSectionRepository;
    @Mock
    private SectionTemplateRepository sectionTemplateRepository;
    @Mock
    private InviteLinkRepository inviteLinkRepository;
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ProjectService projectService;

    @Test
    @DisplayName("프로젝트를 생성하면 생성자가 OWNER 로 등록되고 유형별 고정 6섹션이 생성된다")
    void create_registersOwner_andCreatesSixSections() {
        Long userId = 1L;
        User owner = User.builder().name("Wevo").status(UserStatus.ACTIVE).build();
        ReflectionTestUtils.setField(owner, "id", userId);
        ProjectCreateRequest request = new ProjectCreateRequest(
                "발표 프로젝트", "우리 팀 아이디어 발표를 준비합니다.", OutputType.PRESENTATION, "심사위원");

        given(userRepository.findByIdForUpdate(userId)).willReturn(Optional.of(owner));
        given(projectRepository.save(any(Project.class))).willAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            ReflectionTestUtils.setField(project, "id", 100L);
            return project;
        });
        given(sectionTemplateRepository.findByResultTypeOrderByOrderNo(OutputType.PRESENTATION))
                .willReturn(sixTemplates());
        given(projectSectionRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

        ProjectCreateResponse response = projectService.create(userId, request);

        assertThat(response.projectId()).isEqualTo(100L);
        assertThat(response.resultType()).isEqualTo(OutputType.PRESENTATION);
        assertThat(response.myRole()).isEqualTo(ProjectMemberRole.OWNER);
        assertThat(response.sections()).hasSize(6);
        assertThat(response.sections())
                .allSatisfy(section -> assertThat(section.sectionStatus()).isEqualTo(ProjectSectionStatus.COLLECTING));
        assertThat(response.sections().get(0).keyQuestion()).isNotBlank();

        ArgumentCaptor<ProjectMember> memberCaptor = ArgumentCaptor.forClass(ProjectMember.class);
        verify(projectMemberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getRole()).isEqualTo(ProjectMemberRole.OWNER);
        verify(projectSectionRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("title 을 생략하면 서버 기본값으로 저장해 응답 title 이 항상 값을 갖는다 (API_SPEC §3.2.1)")
    void create_withoutTitle_savesDefaultTitle() {
        Long userId = 1L;
        User owner = User.builder().name("Wevo").status(UserStatus.ACTIVE).build();
        ReflectionTestUtils.setField(owner, "id", userId);
        ProjectCreateRequest request = new ProjectCreateRequest(
                "  ", "우리 팀 아이디어 발표를 준비합니다.", OutputType.PRESENTATION, "심사위원");

        given(userRepository.findByIdForUpdate(userId)).willReturn(Optional.of(owner));
        given(projectRepository.save(any(Project.class))).willAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            ReflectionTestUtils.setField(project, "id", 100L);
            return project;
        });
        given(sectionTemplateRepository.findByResultTypeOrderByOrderNo(OutputType.PRESENTATION))
                .willReturn(sixTemplates());
        given(projectSectionRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

        projectService.create(userId, request);

        ArgumentCaptor<Project> projectCaptor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(projectCaptor.capture());
        assertThat(projectCaptor.getValue().getTitle()).isEqualTo(ProjectService.DEFAULT_TITLE);
    }

    @Test
    @DisplayName("존재하지 않는 사용자가 생성하면 USER_NOT_FOUND 예외를 던진다")
    void create_userNotFound_throws() {
        given(userRepository.findByIdForUpdate(99L)).willReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () -> projectService.create(
                99L, new ProjectCreateRequest(null, "아이디어 텍스트", OutputType.PROPOSAL, null)));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("참여 시각은 서버 시간대와 무관하게 KST 로 기록된다")
    void create_recordsJoinedAtInKst() {
        Long userId = 1L;
        User owner = User.builder().name("Wevo").status(UserStatus.ACTIVE).build();
        ReflectionTestUtils.setField(owner, "id", userId);

        given(userRepository.findByIdForUpdate(userId)).willReturn(Optional.of(owner));
        given(projectRepository.save(any(Project.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(sectionTemplateRepository.findByResultTypeOrderByOrderNo(OutputType.PRESENTATION))
                .willReturn(sixTemplates());
        given(projectSectionRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

        projectService.create(userId, new ProjectCreateRequest(
                "발표 프로젝트", "아이디어", OutputType.PRESENTATION, "심사위원"));

        ArgumentCaptor<ProjectMember> memberCaptor = ArgumentCaptor.forClass(ProjectMember.class);
        verify(projectMemberRepository).save(memberCaptor.capture());
        // JVM 기본 시간대(UTC 배포 환경)를 따랐다면 9시간 어긋나 이 검증에서 걸린다
        assertThat(memberCaptor.getValue().getJoinedAt())
                .isCloseTo(LocalDateTime.now(ZoneId.of("Asia/Seoul")),
                        within(1, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("멤버 목록은 참여자 정보와 정원(4)을 함께 반환한다")
    void getMembers_returnsMembersWithCapacity() {
        User owner = user(1L, "김위보", "https://cdn.example/a.png");
        User member = user(2L, "이팀원", null);
        given(projectMemberRepository.findByProjectIdAndUserId(100L, 1L))
                .willReturn(Optional.of(membership(owner, ProjectMemberRole.OWNER)));
        given(projectMemberRepository.findAllWithUserByProjectId(100L)).willReturn(List.of(
                membership(owner, ProjectMemberRole.OWNER),
                membership(member, ProjectMemberRole.MEMBER)));
        // memberCount 는 목록 길이가 아니라 활성 인원이다 — 목록에는 탈퇴자도 남기 때문이다.
        given(projectMemberRepository.countActiveByProjectId(100L)).willReturn(2L);

        ProjectMemberListResponse response = projectService.getMembers(1L, 100L);

        assertThat(response.memberCount()).isEqualTo(2);
        assertThat(response.maxMembers()).isEqualTo(Project.MAX_MEMBERS);
        assertThat(response.members()).extracting(
                        ProjectMemberListResponse.MemberSummary::userId,
                        ProjectMemberListResponse.MemberSummary::name,
                        ProjectMemberListResponse.MemberSummary::profileImageUrl,
                        ProjectMemberListResponse.MemberSummary::role)
                .containsExactly(
                        tuple(1L, "김위보", "https://cdn.example/a.png", ProjectMemberRole.OWNER),
                        tuple(2L, "이팀원", null, ProjectMemberRole.MEMBER));
    }

    @Test
    @DisplayName("비멤버가 멤버 목록을 조회하면 PROJECT_NOT_FOUND 로 존재를 숨긴다")
    void getMembers_nonMember_hidesProject() {
        given(projectMemberRepository.findByProjectIdAndUserId(100L, 99L)).willReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> projectService.getMembers(99L, 100L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND);
        verify(projectMemberRepository, never()).findAllWithUserByProjectId(any());
    }

    @Test
    @DisplayName("OWNER 가 삭제하면 프로젝트가 ARCHIVED 로 전이되고 활성 초대 링크가 비활성화된다")
    void archiveProject_ownerArchivesAndDeactivatesInviteLinks() {
        User owner = user(1L, "김위보", null);
        Project project = project(100L, ProjectStatus.ACTIVE);
        InviteLink activeLink = InviteLink.issue(project, owner, "token-1");
        given(projectMemberRepository.findByProjectIdAndUserId(100L, 1L))
                .willReturn(Optional.of(membership(project, owner, ProjectMemberRole.OWNER)));
        given(inviteLinkRepository.findAllByProjectIdAndIsActiveTrue(100L))
                .willReturn(List.of(activeLink));

        projectService.archiveProject(1L, 100L);

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);
        assertThat(activeLink.getIsActive()).isFalse();
        // 외부 검토 링크 종료를 위해 보관 이벤트를 발행한다 — review 리스너가 같은 트랜잭션에서 처리. (#230)
        verify(eventPublisher).publishEvent(
                new com.wevo.backend.project.domain.ProjectArchivedEvent(100L));
    }

    @Test
    @DisplayName("MEMBER 가 삭제를 시도하면 FORBIDDEN 으로 거부하고 상태를 바꾸지 않는다")
    void archiveProject_member_forbidden() {
        User member = user(2L, "이팀원", null);
        Project project = project(100L, ProjectStatus.ACTIVE);
        given(projectMemberRepository.findByProjectIdAndUserId(100L, 2L))
                .willReturn(Optional.of(membership(project, member, ProjectMemberRole.MEMBER)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> projectService.archiveProject(2L, 100L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
        verify(inviteLinkRepository, never()).findAllByProjectIdAndIsActiveTrue(any());
    }

    @Test
    @DisplayName("비멤버가 삭제를 시도하면 PROJECT_NOT_FOUND 로 존재를 숨긴다")
    void archiveProject_nonMember_hidesProject() {
        given(projectMemberRepository.findByProjectIdAndUserId(100L, 99L)).willReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> projectService.archiveProject(99L, 100L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND);
        verify(inviteLinkRepository, never()).findAllByProjectIdAndIsActiveTrue(any());
    }

    @Test
    @DisplayName("이미 ARCHIVED 인 프로젝트를 다시 삭제하면 멱등하게 성공하고 링크를 다시 건드리지 않는다")
    void archiveProject_alreadyArchived_isIdempotent() {
        User owner = user(1L, "김위보", null);
        Project project = project(100L, ProjectStatus.ARCHIVED);
        given(projectMemberRepository.findByProjectIdAndUserId(100L, 1L))
                .willReturn(Optional.of(membership(project, owner, ProjectMemberRole.OWNER)));

        projectService.archiveProject(1L, 100L);

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);
        verify(inviteLinkRepository, never()).findAllByProjectIdAndIsActiveTrue(any());
    }

    @Test
    @DisplayName("OWNER 가 수정하면 title·description 이 바뀌고 갱신된 상세를 반환한다")
    void updateProject_ownerUpdatesTitleAndDescription() {
        User owner = user(1L, "김위보", null);
        Project project = project(100L, ProjectStatus.ACTIVE);
        given(projectMemberRepository.findByProjectIdAndUserId(100L, 1L))
                .willReturn(Optional.of(membership(project, owner, ProjectMemberRole.OWNER)));
        given(projectMemberRepository.countActiveByProjectId(100L)).willReturn(2L);
        given(projectSectionRepository.findByProjectIdOrderBySectionOrder(100L))
                .willReturn(List.of());

        ProjectDetailResponse response = projectService.updateProject(1L, 100L,
                new ProjectUpdateRequest("위보 중간발표", "10월 중간발표용."));

        assertThat(project.getTitle()).isEqualTo("위보 중간발표");
        assertThat(project.getDescription()).isEqualTo("10월 중간발표용.");
        assertThat(response.title()).isEqualTo("위보 중간발표");
        assertThat(response.description()).isEqualTo("10월 중간발표용.");
        assertThat(response.memberCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("요청에 없는 필드는 기존값을 유지한다 (부분 수정)")
    void updateProject_nullFieldsKeepExistingValues() {
        User owner = user(1L, "김위보", null);
        Project project = project(100L, ProjectStatus.ACTIVE);
        project.changeDescription("기존 설명");
        given(projectMemberRepository.findByProjectIdAndUserId(100L, 1L))
                .willReturn(Optional.of(membership(project, owner, ProjectMemberRole.OWNER)));
        given(projectSectionRepository.findByProjectIdOrderBySectionOrder(100L))
                .willReturn(List.of());

        projectService.updateProject(1L, 100L, new ProjectUpdateRequest(null, null));

        assertThat(project.getTitle()).isEqualTo("위보 발표 준비");
        assertThat(project.getDescription()).isEqualTo("기존 설명");
    }

    @Test
    @DisplayName("description 에 빈 문자열을 보내면 설명을 지운다")
    void updateProject_blankDescriptionClearsIt() {
        User owner = user(1L, "김위보", null);
        Project project = project(100L, ProjectStatus.ACTIVE);
        project.changeDescription("기존 설명");
        given(projectMemberRepository.findByProjectIdAndUserId(100L, 1L))
                .willReturn(Optional.of(membership(project, owner, ProjectMemberRole.OWNER)));
        given(projectSectionRepository.findByProjectIdOrderBySectionOrder(100L))
                .willReturn(List.of());

        projectService.updateProject(1L, 100L, new ProjectUpdateRequest(null, ""));

        // 저장 값이 "없음"인지 "공백"인지 갈리지 않도록 null 로 정규화한다. (§1.4 — 응답에서 생략)
        assertThat(project.getDescription()).isNull();
    }

    @Test
    @DisplayName("MEMBER 가 수정을 시도하면 FORBIDDEN 으로 거부하고 값을 바꾸지 않는다")
    void updateProject_member_forbidden() {
        User member = user(2L, "이팀원", null);
        Project project = project(100L, ProjectStatus.ACTIVE);
        given(projectMemberRepository.findByProjectIdAndUserId(100L, 2L))
                .willReturn(Optional.of(membership(project, member, ProjectMemberRole.MEMBER)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> projectService.updateProject(2L, 100L,
                        new ProjectUpdateRequest("바꾼 이름", null)));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(project.getTitle()).isEqualTo("위보 발표 준비");
    }

    @Test
    @DisplayName("비멤버가 수정을 시도하면 PROJECT_NOT_FOUND 로 존재를 숨긴다")
    void updateProject_nonMember_hidesProject() {
        given(projectMemberRepository.findByProjectIdAndUserId(100L, 99L)).willReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> projectService.updateProject(99L, 100L,
                        new ProjectUpdateRequest("바꾼 이름", null)));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND);
        verify(projectSectionRepository, never()).findByProjectIdOrderBySectionOrder(any());
    }

    @Test
    @DisplayName("보관된 프로젝트도 수정할 수 있다 — 상태는 그대로 ARCHIVED 다")
    void updateProject_archivedProjectIsStillEditable() {
        User owner = user(1L, "김위보", null);
        Project project = project(100L, ProjectStatus.ARCHIVED);
        given(projectMemberRepository.findByProjectIdAndUserId(100L, 1L))
                .willReturn(Optional.of(membership(project, owner, ProjectMemberRole.OWNER)));
        given(projectSectionRepository.findByProjectIdOrderBySectionOrder(100L))
                .willReturn(List.of());

        projectService.updateProject(1L, 100L, new ProjectUpdateRequest("보관본 이름", null));

        assertThat(project.getTitle()).isEqualTo("보관본 이름");
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);
    }

    private Project project(Long id, ProjectStatus status) {
        Project project = Project.builder()
                .title("위보 발표 준비")
                .resultType(OutputType.PRESENTATION)
                .audience("심사위원")
                .status(status)
                .build();
        ReflectionTestUtils.setField(project, "id", id);
        return project;
    }

    private ProjectMember membership(Project project, User user, ProjectMemberRole role) {
        return ProjectMember.builder()
                .project(project)
                .user(user)
                .role(role)
                .joinedAt(LocalDateTime.now())
                .build();
    }

    private User user(Long id, String name, String profileImageUrl) {
        User user = User.builder()
                .name(name)
                .email("user" + id + "@wevo.com")
                .profileImageUrl(profileImageUrl)
                .status(UserStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private ProjectMember membership(User user, ProjectMemberRole role) {
        return ProjectMember.builder()
                .project(Project.builder().title("위보 발표 준비").build())
                .user(user)
                .role(role)
                .joinedAt(LocalDateTime.now())
                .build();
    }

    private List<SectionTemplate> sixTemplates() {
        return IntStream.rangeClosed(1, 6)
                .mapToObj(i -> SectionTemplate.builder()
                        .resultType(OutputType.PRESENTATION)
                        .sectionKey("key-" + i)
                        .title("섹션 " + i)
                        .description("핵심 질문 " + i)
                        .guideText("작성 가이드 " + i)
                        .orderNo(i)
                        .isRequired(true)
                        .build())
                .toList();
    }
}
