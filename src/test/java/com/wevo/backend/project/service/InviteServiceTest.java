package com.wevo.backend.project.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.domain.InviteLink;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.dto.response.InviteLinkResponse;
import com.wevo.backend.project.dto.response.InvitePreviewResponse;
import com.wevo.backend.project.dto.response.ProjectJoinResponse;
import com.wevo.backend.project.repository.InviteLinkRepository;
import com.wevo.backend.project.repository.ProjectMemberRepository;
import com.wevo.backend.project.repository.ProjectRepository;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import com.wevo.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InviteServiceTest {

    private static final Long PROJECT_ID = 10L;
    private static final Long OWNER_ID = 1L;
    private static final Long MEMBER_ID = 2L;
    private static final Long JOINER_ID = 3L;
    private static final String BASE_URL = "http://localhost:3000/invite/";
    private static final String TOKEN = "tok123abc";

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private InviteLinkRepository inviteLinkRepository;
    @Mock
    private UserRepository userRepository;

    private InviteService inviteService;

    @BeforeEach
    void setUp() {
        inviteService = new InviteService(
                projectRepository, projectMemberRepository, inviteLinkRepository, userRepository, BASE_URL);
    }

    // ---------- 초대 링크 생성 ----------

    @Test
    @DisplayName("OWNER가 링크를 생성하면 토큰과 공유 URL을 반환한다")
    void createInviteLink_asOwner_createsLink() {
        Project project = project();
        User owner = user(OWNER_ID, "팀장");
        given(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, OWNER_ID))
                .willReturn(Optional.of(member(project, owner, ProjectMemberRole.OWNER)));
        given(inviteLinkRepository.findFirstByProjectIdAndIsActiveTrue(PROJECT_ID)).willReturn(Optional.empty());
        given(inviteLinkRepository.save(any(InviteLink.class)))
                .willAnswer(i -> i.getArgument(0));

        InviteLinkResponse response = inviteService.createInviteLink(OWNER_ID, PROJECT_ID);

        assertThat(response.token()).isNotBlank();
        assertThat(response.inviteUrl()).isEqualTo(BASE_URL + response.token());
        verify(inviteLinkRepository).save(any(InviteLink.class));
    }

    @Test
    @DisplayName("base-url에 trailing slash가 없어도 정확히 하나만 붙여 URL을 만든다")
    void createInviteLink_normalizesTrailingSlash() {
        InviteService noSlash = new InviteService(
                projectRepository, projectMemberRepository, inviteLinkRepository, userRepository,
                "http://host/invite"); // 끝에 슬래시 없음
        Project project = project();
        User owner = user(OWNER_ID, "팀장");
        given(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, OWNER_ID))
                .willReturn(Optional.of(member(project, owner, ProjectMemberRole.OWNER)));
        given(inviteLinkRepository.findFirstByProjectIdAndIsActiveTrue(PROJECT_ID))
                .willReturn(Optional.of(InviteLink.issue(project, owner, TOKEN)));

        InviteLinkResponse response = noSlash.createInviteLink(OWNER_ID, PROJECT_ID);

        assertThat(response.inviteUrl()).isEqualTo("http://host/invite/" + TOKEN);
    }

    @Test
    @DisplayName("이미 활성 링크가 있으면 새로 만들지 않고 기존 링크를 반환한다 (재사용)")
    void createInviteLink_reusesExistingActiveLink() {
        Project project = project();
        User owner = user(OWNER_ID, "팀장");
        given(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, OWNER_ID))
                .willReturn(Optional.of(member(project, owner, ProjectMemberRole.OWNER)));
        given(inviteLinkRepository.findFirstByProjectIdAndIsActiveTrue(PROJECT_ID))
                .willReturn(Optional.of(InviteLink.issue(project, owner, TOKEN)));

        InviteLinkResponse response = inviteService.createInviteLink(OWNER_ID, PROJECT_ID);

        assertThat(response.token()).isEqualTo(TOKEN);
        assertThat(response.inviteUrl()).isEqualTo(BASE_URL + TOKEN);
        verify(inviteLinkRepository, never()).save(any(InviteLink.class));
    }

    @Test
    @DisplayName("멤버가 아니면 PROJECT_NOT_FOUND (존재 숨김)")
    void createInviteLink_notMember_throwsProjectNotFound() {
        given(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, OWNER_ID))
                .willReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> inviteService.createInviteLink(OWNER_ID, PROJECT_ID));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND);
    }

    @Test
    @DisplayName("멤버지만 OWNER가 아니면 FORBIDDEN")
    void createInviteLink_notOwner_throwsForbidden() {
        Project project = project();
        User member = user(MEMBER_ID, "팀원");
        given(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, MEMBER_ID))
                .willReturn(Optional.of(member(project, member, ProjectMemberRole.MEMBER)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> inviteService.createInviteLink(MEMBER_ID, PROJECT_ID));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        verify(inviteLinkRepository, never()).save(any(InviteLink.class));
    }

    // ---------- 미리보기 ----------

    @Test
    @DisplayName("유효한 토큰이면 프로젝트명·인원과 함께 미리보기를 반환한다")
    void getInvitePreview_validToken_returnsPreview() {
        Project project = project();
        given(inviteLinkRepository.findActiveWithProjectByToken(TOKEN))
                .willReturn(Optional.of(InviteLink.issue(project, user(OWNER_ID, "팀장"), TOKEN)));
        given(projectMemberRepository.countByProjectId(PROJECT_ID)).willReturn(2L);

        InvitePreviewResponse response = inviteService.getInvitePreview(TOKEN);

        assertThat(response.projectId()).isEqualTo(PROJECT_ID);
        assertThat(response.projectTitle()).isEqualTo("테스트프로젝트");
        assertThat(response.memberCount()).isEqualTo(2L);
        assertThat(response.maxMembers()).isEqualTo(Project.MAX_MEMBERS);
        assertThat(response.full()).isFalse();
    }

    @Test
    @DisplayName("무효/비활성 토큰이면 INVITE_LINK_NOT_FOUND")
    void getInvitePreview_invalidToken_throws() {
        given(inviteLinkRepository.findActiveWithProjectByToken("bad")).willReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> inviteService.getInvitePreview("bad"));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVITE_LINK_NOT_FOUND);
    }

    // ---------- 참여 ----------

    @Test
    @DisplayName("정상 참여하면 MEMBER 역할로 저장하고 프로젝트 정보를 반환한다")
    void joinByToken_newMember_joinsAsMember() {
        Project project = project();
        User joiner = user(JOINER_ID, "새멤버");
        given(inviteLinkRepository.findActiveWithProjectByToken(TOKEN))
                .willReturn(Optional.of(InviteLink.issue(project, user(OWNER_ID, "팀장"), TOKEN)));
        given(projectRepository.findByIdForUpdate(PROJECT_ID)).willReturn(Optional.of(project));
        given(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, JOINER_ID)).willReturn(Optional.empty());
        given(projectMemberRepository.countByProjectId(PROJECT_ID)).willReturn(2L);
        given(userRepository.findById(JOINER_ID)).willReturn(Optional.of(joiner));
        given(projectMemberRepository.save(any(ProjectMember.class))).willAnswer(i -> i.getArgument(0));

        ProjectJoinResponse response = inviteService.joinByToken(JOINER_ID, TOKEN);

        assertThat(response.projectId()).isEqualTo(PROJECT_ID);
        assertThat(response.role()).isEqualTo(ProjectMemberRole.MEMBER);
        verify(projectMemberRepository).save(any(ProjectMember.class));
    }

    @Test
    @DisplayName("이미 멤버면 기존 역할 그대로 멱등하게 반환하고 새로 저장하지 않는다")
    void joinByToken_alreadyMember_idempotent() {
        Project project = project();
        User owner = user(OWNER_ID, "팀장");
        given(inviteLinkRepository.findActiveWithProjectByToken(TOKEN))
                .willReturn(Optional.of(InviteLink.issue(project, owner, TOKEN)));
        given(projectRepository.findByIdForUpdate(PROJECT_ID)).willReturn(Optional.of(project));
        given(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, OWNER_ID))
                .willReturn(Optional.of(member(project, owner, ProjectMemberRole.OWNER)));

        ProjectJoinResponse response = inviteService.joinByToken(OWNER_ID, TOKEN);

        assertThat(response.role()).isEqualTo(ProjectMemberRole.OWNER);
        verify(projectMemberRepository, never()).save(any(ProjectMember.class));
    }

    @Test
    @DisplayName("인원이 상한(4)에 도달하면 PROJECT_MEMBER_LIMIT_EXCEEDED")
    void joinByToken_full_throwsLimitExceeded() {
        Project project = project();
        given(inviteLinkRepository.findActiveWithProjectByToken(TOKEN))
                .willReturn(Optional.of(InviteLink.issue(project, user(OWNER_ID, "팀장"), TOKEN)));
        given(projectRepository.findByIdForUpdate(PROJECT_ID)).willReturn(Optional.of(project));
        given(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, JOINER_ID)).willReturn(Optional.empty());
        given(projectMemberRepository.countByProjectId(PROJECT_ID)).willReturn((long) Project.MAX_MEMBERS);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> inviteService.joinByToken(JOINER_ID, TOKEN));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PROJECT_MEMBER_LIMIT_EXCEEDED);
        verify(projectMemberRepository, never()).save(any(ProjectMember.class));
    }

    @Test
    @DisplayName("무효/비활성 토큰으로 참여하면 INVITE_LINK_NOT_FOUND")
    void joinByToken_invalidToken_throws() {
        given(inviteLinkRepository.findActiveWithProjectByToken("bad")).willReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> inviteService.joinByToken(JOINER_ID, "bad"));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVITE_LINK_NOT_FOUND);
    }

    // ---------- fixtures ----------

    private Project project() {
        Project project = Project.builder().title("테스트프로젝트").build();
        ReflectionTestUtils.setField(project, "id", PROJECT_ID);
        return project;
    }

    private User user(Long id, String name) {
        User user = User.builder()
                .name(name).email(name + "@wevo.com").status(UserStatus.ACTIVE).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private ProjectMember member(Project project, User user, ProjectMemberRole role) {
        return ProjectMember.builder()
                .project(project).user(user).role(role).joinedAt(LocalDateTime.now()).build();
    }
}
