package com.wevo.backend.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.project.domain.InviteLink;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로젝트 삭제(보관 처리) 전 경로를 실제 컨텍스트(H2)로 검증한다. (API_SPEC §3.2.9)
 *
 * <p>삭제는 하드 삭제가 아니라 상태 전이이므로, "목록에서 빠지는가"와 "초대 링크가 막히는가"가
 * 사용자에게 드러나는 실제 효과다. 두 가지 모두 조회 쿼리·다른 API 경로가 책임지므로 단위
 * 테스트로는 검증할 수 없어 여기서 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectDeleteIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private MockMvc mockMvc;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("OWNER 가 삭제하면 204 를 반환하고 내 프로젝트 목록에서 빠진다")
    void delete_byOwner_removesFromList() throws Exception {
        User owner = persistUser("owner-delete-1@wevo.com", "김위보");
        Project kept = persistProject(owner, "남는 프로젝트");
        Project deleted = persistProject(owner, "지울 프로젝트");
        persistMember(kept, owner, ProjectMemberRole.OWNER);
        persistMember(deleted, owner, ProjectMemberRole.OWNER);
        em.flush();

        mockMvc.perform(delete("/api/projects/{projectId}", deleted.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isNoContent());
        em.flush();
        em.clear();

        mockMvc.perform(get("/api/projects").with(authentication(authOf(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("남는 프로젝트"));
    }

    @Test
    @DisplayName("삭제해도 상세 조회는 계속 동작한다 — 목록에서만 빠진다")
    void delete_keepsDetailAccessible() throws Exception {
        User owner = persistUser("owner-delete-2@wevo.com", "김위보");
        Project project = persistProject(owner, "보관될 프로젝트");
        persistMember(project, owner, ProjectMemberRole.OWNER);
        em.flush();

        mockMvc.perform(delete("/api/projects/{projectId}", project.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isNoContent());
        em.flush();
        em.clear();

        mockMvc.perform(get("/api/projects/{projectId}", project.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
    }

    @Test
    @DisplayName("삭제하면 활성 초대 링크가 막혀 미리보기가 404(P003) 로 거부된다")
    void delete_deactivatesInviteLink() throws Exception {
        User owner = persistUser("owner-delete-3@wevo.com", "김위보");
        User outsider = persistUser("outsider-delete-3@wevo.com", "이외부");
        Project project = persistProject(owner, "초대 링크가 있는 프로젝트");
        persistMember(project, owner, ProjectMemberRole.OWNER);
        InviteLink link = InviteLink.issue(project, owner, "delete-token-3");
        em.persist(link);
        em.flush();

        mockMvc.perform(delete("/api/projects/{projectId}", project.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isNoContent());
        em.flush();
        em.clear();

        mockMvc.perform(get("/api/invites/{token}", "delete-token-3")
                        .with(authentication(authOf(outsider))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("P003"));
    }

    @Test
    @DisplayName("MEMBER 가 삭제를 시도하면 403(A002) 로 거부하고 상태를 바꾸지 않는다")
    void delete_byMember_forbidden() throws Exception {
        User owner = persistUser("owner-delete-4@wevo.com", "김위보");
        User member = persistUser("member-delete-4@wevo.com", "이팀원");
        Project project = persistProject(owner, "위보 발표 준비");
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistMember(project, member, ProjectMemberRole.MEMBER);
        em.flush();

        mockMvc.perform(delete("/api/projects/{projectId}", project.getId())
                        .with(authentication(authOf(member))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A002"));
        em.flush();
        em.clear();

        assertThat(em.find(Project.class, project.getId()).getStatus())
                .isEqualTo(ProjectStatus.ACTIVE);
    }

    @Test
    @DisplayName("비멤버가 삭제를 시도하면 404(P001) 로 프로젝트 존재를 숨긴다")
    void delete_byNonMember_hidesProject() throws Exception {
        User owner = persistUser("owner-delete-5@wevo.com", "김위보");
        User outsider = persistUser("outsider-delete-5@wevo.com", "이외부");
        Project project = persistProject(owner, "남의 프로젝트");
        persistMember(project, owner, ProjectMemberRole.OWNER);
        em.flush();

        mockMvc.perform(delete("/api/projects/{projectId}", project.getId())
                        .with(authentication(authOf(outsider))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("P001"));
    }

    @Test
    @DisplayName("이미 삭제한 프로젝트를 다시 삭제해도 204 로 멱등하게 성공한다")
    void delete_twice_isIdempotent() throws Exception {
        User owner = persistUser("owner-delete-6@wevo.com", "김위보");
        Project project = persistProject(owner, "두 번 지울 프로젝트");
        persistMember(project, owner, ProjectMemberRole.OWNER);
        em.flush();

        mockMvc.perform(delete("/api/projects/{projectId}", project.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isNoContent());
        em.flush();

        mockMvc.perform(delete("/api/projects/{projectId}", project.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isNoContent());
        em.flush();
        em.clear();

        assertThat(em.find(Project.class, project.getId()).getStatus())
                .isEqualTo(ProjectStatus.ARCHIVED);
    }

    @Test
    @DisplayName("인증 없이 삭제하면 401(A001)")
    void delete_withoutAuth_unauthorized() throws Exception {
        User owner = persistUser("owner-delete-7@wevo.com", "김위보");
        Project project = persistProject(owner, "위보 발표 준비");
        persistMember(project, owner, ProjectMemberRole.OWNER);
        em.flush();

        mockMvc.perform(delete("/api/projects/{projectId}", project.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A001"));
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

    private Project persistProject(User owner, String title) {
        Project project = Project.builder()
                .owner(owner)
                .title(title)
                .resultType(OutputType.PRESENTATION)
                .audience("심사위원")
                .status(ProjectStatus.ACTIVE)
                .build();
        em.persist(project);
        return project;
    }

    private ProjectMember persistMember(Project project, User user, ProjectMemberRole role) {
        ProjectMember member = ProjectMember.builder()
                .project(project).user(user).role(role)
                .joinedAt(LocalDateTime.now(KST)).build();
        em.persist(member);
        return member;
    }
}
