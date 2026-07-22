package com.wevo.backend.project;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로젝트 멤버 목록 조회 전 경로를 실제 컨텍스트(H2)로 검증한다. (API_SPEC §3.2.8)
 *
 * <p>정렬(OWNER 우선 → 참여 시각)은 조회 쿼리가 책임지므로 단위 테스트로는 검증할 수 없어
 * 여기서 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectMemberListIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private MockMvc mockMvc;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("팀장이 먼저, 팀원은 참여 시각 순으로 정렬해 반환한다")
    void returnsMembersOwnerFirstThenJoinedAt() throws Exception {
        User owner = persistUser("owner-members-1@wevo.com", "김위보");
        User early = persistUser("early-members-1@wevo.com", "이팀원");
        User late = persistUser("late-members-1@wevo.com", "박팀원");
        Project project = persistProject(owner);
        LocalDateTime base = LocalDateTime.now(KST);
        // 팀장을 가장 늦게 참여시켜, 정렬이 참여 시각이 아니라 역할을 우선하는지 확인한다
        persistMember(project, late, ProjectMemberRole.MEMBER, base.minusHours(1));
        persistMember(project, early, ProjectMemberRole.MEMBER, base.minusHours(2));
        persistMember(project, owner, ProjectMemberRole.OWNER, base);
        em.flush();
        em.clear();

        getMembers(project.getId(), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.memberCount").value(3))
                .andExpect(jsonPath("$.data.maxMembers").value(Project.MAX_MEMBERS))
                .andExpect(jsonPath("$.data.members[0].name").value("김위보"))
                .andExpect(jsonPath("$.data.members[0].role").value("OWNER"))
                .andExpect(jsonPath("$.data.members[1].name").value("이팀원"))
                .andExpect(jsonPath("$.data.members[2].name").value("박팀원"));
    }

    @Test
    @DisplayName("프로필 이미지가 없으면 해당 필드를 생략하고, 이메일은 반환하지 않는다")
    void omitsMissingProfileImageAndNeverExposesEmail() throws Exception {
        User owner = persistUser("owner-members-2@wevo.com", "김위보");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER, LocalDateTime.now(KST));
        em.flush();
        em.clear();

        getMembers(project.getId(), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members[0].userId").value(owner.getId()))
                .andExpect(jsonPath("$.data.members[0].joinedAt").exists())
                .andExpect(jsonPath("$.data.members[0].profileImageUrl").doesNotExist())
                .andExpect(jsonPath("$.data.members[0].email").doesNotExist());
    }

    @Test
    @DisplayName("혼자인 프로젝트도 팀장 1명을 그대로 반환한다")
    void returnsSingleOwner() throws Exception {
        User owner = persistUser("owner-members-3@wevo.com", "김위보");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER, LocalDateTime.now(KST));
        em.flush();
        em.clear();

        getMembers(project.getId(), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberCount").value(1))
                .andExpect(jsonPath("$.data.members.length()").value(1));
    }

    @Test
    @DisplayName("프로젝트 멤버가 아니면 404(P001) — 프로젝트 존재를 숨긴다")
    void nonMemberHiddenAsNotFound() throws Exception {
        User owner = persistUser("owner-members-4@wevo.com", "김위보");
        User outsider = persistUser("outsider-members-4@wevo.com", "남남");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER, LocalDateTime.now(KST));
        em.flush();

        getMembers(project.getId(), outsider)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("P001"));
    }

    @Test
    @DisplayName("존재하지 않는 프로젝트를 조회하면 404(P001)")
    void unknownProject() throws Exception {
        User user = persistUser("user-members-5@wevo.com", "김위보");
        em.flush();

        getMembers(999_999L, user)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("P001"));
    }

    // --- 헬퍼 ---

    private ResultActions getMembers(Long projectId, User user) throws Exception {
        return mockMvc.perform(get("/api/projects/{id}/members", projectId)
                .with(authentication(authOf(user))));
    }

    private UsernamePasswordAuthenticationToken authOf(User user) {
        return new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(user.getId()), null, AuthorityUtils.NO_AUTHORITIES);
    }

    private User persistUser(String email, String name) {
        User user = User.builder().name(name).email(email).status(UserStatus.ACTIVE).build();
        em.persist(user);
        return user;
    }

    private Project persistProject(User owner) {
        Project project = Project.builder()
                .owner(owner)
                .title("위보 발표 준비")
                .resultType(OutputType.PRESENTATION)
                .audience("심사위원")
                .status(ProjectStatus.ACTIVE)
                .build();
        em.persist(project);
        return project;
    }

    private ProjectMember persistMember(Project project, User user, ProjectMemberRole role,
                                        LocalDateTime joinedAt) {
        ProjectMember member = ProjectMember.builder()
                .project(project).user(user).role(role).joinedAt(joinedAt).build();
        em.persist(member);
        return member;
    }
}
