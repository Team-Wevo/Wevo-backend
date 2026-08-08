package com.wevo.backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wevo.backend.auth.domain.AuthAccount;
import com.wevo.backend.auth.domain.AuthProvider;
import com.wevo.backend.auth.service.RefreshTokenService;
import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴 전 경로를 실제 PostgreSQL 컨텍스트로 검증한다. (API_SPEC §3.3.3)
 *
 * <p>단위 테스트({@code UserServiceTest})가 못 잡는 것을 본다 — 도메인 이벤트가 실제로 auth
 * 도메인까지 배선됐는지, {@code auth_accounts} 삭제가 FK 제약과 함께 성립하는지, OWNER 판별
 * 쿼리가 보관 상태를 제대로 걸러내는지다.
 *
 * <p>Refresh Token 폐기는 Redis 를 쓰므로 여기서는 호출 여부만 확인한다. Redis 동작 자체는
 * {@code RefreshTokenServiceTest} 가 검증한다.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Import(PostgresTestContainerConfig.class)
@AutoConfigureMockMvc
@Transactional
class UserWithdrawalIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("탈퇴하면 계정 행은 남고 개인 식별정보와 소셜 연결만 사라진다")
    void withdrawKeepsRowAndScrubsIdentity() throws Exception {
        User user = persistUser("호석", "hoseok@wevo.com");
        persistAuthAccount(user);

        mockMvc.perform(delete("/api/users/me").with(authenticationOf(user)))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();

        User reloaded = entityManager.find(User.class, user.getId());
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(reloaded.getName()).isEqualTo(User.WITHDRAWN_NAME);
        assertThat(reloaded.getEmail()).isNull();
        assertThat(reloaded.getProfileImageUrl()).isNull();

        // 소셜 연결이 남으면 같은 계정으로 재로그인했을 때 지운 계정에 그대로 붙는다.
        assertThat(countAuthAccounts(user.getId())).isZero();
        then(refreshTokenService).should().delete(user.getId());
    }

    @Test
    @DisplayName("보관되지 않은 프로젝트의 OWNER 면 409(U003)로 거부하고 계정을 그대로 둔다")
    void withdrawIsRejectedWhileOwningActiveProject() throws Exception {
        User user = persistUser("팀장", "owner@wevo.com");
        persistMembership(user, ProjectStatus.ACTIVE, ProjectMemberRole.OWNER);

        mockMvc.perform(delete("/api/users/me").with(authenticationOf(user)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("U003"));

        entityManager.flush();
        entityManager.clear();

        User reloaded = entityManager.find(User.class, user.getId());
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(reloaded.getEmail()).isEqualTo("owner@wevo.com");
        then(refreshTokenService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("보관된 프로젝트의 OWNER 는 탈퇴할 수 있다")
    void withdrawIsAllowedWhenOwnedProjectIsArchived() throws Exception {
        // 보관까지 포함해 막으면 프로젝트를 한 번이라도 만든 사용자는 영영 탈퇴할 수 없다.
        User user = persistUser("팀장", "archived-owner@wevo.com");
        persistMembership(user, ProjectStatus.ARCHIVED, ProjectMemberRole.OWNER);

        mockMvc.perform(delete("/api/users/me").with(authenticationOf(user)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("탈퇴한 멤버는 프로젝트 멤버 목록에 '탈퇴한 사용자'로 남는다")
    void withdrawnMemberStaysInMemberListAsMaskedName() throws Exception {
        // 목록에서 빼면 그 사람이 쓴 의견의 작성자를 화면이 찾지 못한다. 그래서 남기고 이름만 가린다.
        User owner = persistUser("팀장", "keeper@wevo.com");
        Project project = persistMembership(owner, ProjectStatus.ACTIVE, ProjectMemberRole.OWNER);
        User leaver = persistUser("나가는사람", "leaver@wevo.com");
        persistMember(project, leaver, ProjectMemberRole.MEMBER);

        mockMvc.perform(delete("/api/users/me").with(authenticationOf(leaver)))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/projects/{projectId}/members", project.getId())
                        .with(authenticationOf(owner)))
                .andExpect(status().isOk())
                // 목록에는 둘 다 남지만, memberCount 는 정원과 짝이 되는 값이라 탈퇴자를 뺀 수다.
                .andExpect(jsonPath("$.data.members.length()").value(2))
                .andExpect(jsonPath("$.data.memberCount").value(1))
                // 필터 표현식이라 JsonPath 는 리스트를 돌려주지만, JsonPathExpectationsHelper 가
                // 원소 하나짜리 리스트를 벗겨 비교한다. 여러 개가 걸리면 그 자리에서 실패한다.
                .andExpect(jsonPath("$.data.members[?(@.userId == " + leaver.getId() + ")].name")
                        .value(User.WITHDRAWN_NAME));
    }

    @Test
    @DisplayName("탈퇴한 계정은 새 프로젝트를 만들 수 없다 — OWNER 로 되살아나는 경로를 막는다")
    void withdrawnUserCannotCreateProject() throws Exception {
        // 탈퇴 직후에도 Access Token 이 30분간 유효해 실제로 요청이 들어올 수 있다.
        // 막지 않으면 "탈퇴자가 활성 프로젝트의 OWNER" 라는, U003 이 막으려던 상태가 다시 생긴다.
        User user = persistUser("나가는사람", "leaver-create@wevo.com");
        mockMvc.perform(delete("/api/users/me").with(authenticationOf(user)))
                .andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/api/projects")
                        .with(authenticationOf(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ideaText": "팀 의견을 모아 하나의 결과물로 만든다.",
                                  "resultType": "PROPOSAL",
                                  "audience": "교내 심사위원"
                                }"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("U001"));
    }

    private User persistUser(String name, String email) {
        User user = User.builder()
                .name(name)
                .email(email)
                .profileImageUrl("http://img/" + name + ".png")
                .status(UserStatus.ACTIVE)
                .build();
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    private void persistAuthAccount(User user) {
        entityManager.persist(AuthAccount.builder()
                .user(user)
                .provider(AuthProvider.GOOGLE)
                .providerUserId("google-" + user.getId())
                .build());
        entityManager.flush();
    }

    private long countAuthAccounts(Long userId) {
        return entityManager.createQuery(
                        "SELECT COUNT(a) FROM AuthAccount a WHERE a.user.id = :userId", Long.class)
                .setParameter("userId", userId)
                .getSingleResult();
    }

    private Project persistMembership(User user, ProjectStatus status, ProjectMemberRole role) {
        Project project = Project.builder()
                .owner(user)
                .title("위보 제안서")
                .ideaText("팀 의견을 모아 하나의 결과물로 만든다.")
                .resultType(OutputType.PROPOSAL)
                .audience("교내 심사위원")
                .status(status)
                .build();
        entityManager.persist(project);
        persistMember(project, user, role);
        return project;
    }

    private void persistMember(Project project, User user, ProjectMemberRole role) {
        entityManager.persist(ProjectMember.builder()
                .project(project)
                .user(user)
                .role(role)
                .joinedAt(LocalDateTime.now())
                .build());
        entityManager.flush();
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor authenticationOf(User user) {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(user.getId()), null, AuthorityUtils.NO_AUTHORITIES));
    }
}
