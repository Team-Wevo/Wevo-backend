package com.wevo.backend.section;

import static org.assertj.core.api.Assertions.assertThat;

import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.section.domain.DraftLease;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.repository.DraftLeaseRepository;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import com.wevo.backend.user.service.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴 시 그 사용자의 초안 편집 잠금(lease)이 실제로 해제되는지 검증한다. (#217)
 *
 * <p>{@code UserService.withdraw} 를 실제로 호출해 {@code UserWithdrawnEvent} 배선까지 함께 본다 —
 * 리스너({@code WithdrawnUserLeaseCleaner})가 같은 트랜잭션에서 lease 를 비우는지는 실제 DB 로만
 * 확인된다. Redis 를 쓰는 Refresh Token 폐기는 이 시나리오의 대상이 아니라 모킹한다.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Import(PostgresTestContainerConfig.class)
@Transactional
class WithdrawnUserLeaseCleanupIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private UserService userService;
    @Autowired
    private DraftLeaseRepository draftLeaseRepository;

    @MockitoBean
    private com.wevo.backend.auth.service.RefreshTokenService refreshTokenService;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("탈퇴하면 그 사용자가 쥔 활성 lease 가 모두 해제된다")
    void withdraw_releasesAllHeldLeases() {
        User owner = persistUser("owner@wevo.com");
        User leaver = persistUser("leaver@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistMember(project, leaver, ProjectMemberRole.MEMBER);
        // 탈퇴자가 두 섹션의 편집권을 동시에 쥔 상태.
        DraftLease leaseA = persistActiveLease(persistSection(project, 1), leaver);
        DraftLease leaseB = persistActiveLease(persistSection(project, 2), leaver);
        em.flush();

        userService.withdraw(leaver.getId());
        em.flush();
        em.clear();

        LocalDateTime now = LocalDateTime.now(KST);
        assertThat(draftLeaseRepository.findById(leaseA.getId()).orElseThrow().isActiveAt(now))
                .as("탈퇴자가 쥔 lease 는 즉시 비활성이 되어 다른 멤버가 획득할 수 있어야 한다")
                .isFalse();
        assertThat(draftLeaseRepository.findById(leaseB.getId()).orElseThrow().isActiveAt(now))
                .isFalse();
    }

    @Test
    @DisplayName("다른 사용자의 lease 는 탈퇴 정리에 영향받지 않는다")
    void withdraw_doesNotTouchOtherHoldersLease() {
        User owner = persistUser("owner2@wevo.com");
        User leaver = persistUser("leaver2@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistMember(project, leaver, ProjectMemberRole.MEMBER);
        DraftLease ownersLease = persistActiveLease(persistSection(project, 1), owner);
        em.flush();

        userService.withdraw(leaver.getId());
        em.flush();
        em.clear();

        assertThat(draftLeaseRepository.findById(ownersLease.getId()).orElseThrow()
                .isActiveAt(LocalDateTime.now(KST)))
                .as("탈퇴하지 않은 팀장의 편집권은 그대로 유지된다")
                .isTrue();
    }

    private DraftLease persistActiveLease(ProjectSection section, User holder) {
        DraftLease lease = DraftLease.builder()
                .projectSection(section)
                .holderUserId(holder.getId())
                .leaseUntil(LocalDateTime.now(KST).plusMinutes(5))
                .build();
        em.persist(lease);
        return lease;
    }

    private User persistUser(String email) {
        User user = User.builder()
                .name(email.substring(0, email.indexOf('@')))
                .email(email)
                .status(UserStatus.ACTIVE)
                .build();
        em.persist(user);
        return user;
    }

    private Project persistProject(User owner) {
        Project project = Project.builder()
                .owner(owner)
                .title("위보 기획")
                .resultType(OutputType.PRESENTATION)
                .audience("프로젝트 팀원")
                .status(ProjectStatus.ACTIVE)
                .build();
        em.persist(project);
        return project;
    }

    private ProjectSection persistSection(Project project, int order) {
        ProjectSection section = ProjectSection.builder()
                .project(project)
                .title("섹션 " + order)
                .sectionOrder(order)
                .status(ProjectSectionStatus.DRAFTING)
                .build();
        em.persist(section);
        return section;
    }

    private void persistMember(Project project, User user, ProjectMemberRole role) {
        em.persist(ProjectMember.builder()
                .project(project)
                .user(user)
                .role(role)
                .joinedAt(LocalDateTime.now(KST))
                .build());
    }
}
