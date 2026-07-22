package com.wevo.backend.project.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.dto.request.ProjectCreateRequest;
import com.wevo.backend.project.dto.response.ProjectCreateResponse;
import com.wevo.backend.project.dto.response.ProjectMemberListResponse;
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
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
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

        given(userRepository.findById(userId)).willReturn(Optional.of(owner));
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

        given(userRepository.findById(userId)).willReturn(Optional.of(owner));
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
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () -> projectService.create(
                99L, new ProjectCreateRequest(null, "아이디어 텍스트", OutputType.PROPOSAL, null)));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
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
