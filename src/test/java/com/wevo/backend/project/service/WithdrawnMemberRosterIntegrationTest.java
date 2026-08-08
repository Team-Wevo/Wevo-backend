package com.wevo.backend.project.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
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
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * 진행 현황의 분모에서 탈퇴 계정이 빠지는지 실제 PostgreSQL 로 검증한다. (#201, #202)
 *
 * <p>회원 탈퇴는 소프트 삭제라(§3.3.3) {@code project_members} 행이 그대로 남는다. 그 행을 세면
 * 영영 채워지지 않는 미완료가 생기고, 1인 프로젝트 판정(§6.3.1)이 어긋나 섹션 확정까지 막힌다.
 *
 * <p>쿼리로 거르는 동작이라 모킹으로는 검증되지 않는다.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Import(PostgresTestContainerConfig.class)
@Transactional
class WithdrawnMemberRosterIntegrationTest {

    @Autowired
    private ProjectMemberRosterQueryService rosterQueryService;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("탈퇴한 멤버는 참여자 로스터에서 빠진다 — 의견 수집 현황의 분모")
    void withdrawnMemberIsExcludedFromParticipants() {
        User owner = persistUser("팀장", UserStatus.ACTIVE);
        User active = persistUser("남은팀원", UserStatus.ACTIVE);
        User leaver = persistUser("나간팀원", UserStatus.WITHDRAWN);
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistMember(project, active, ProjectMemberRole.MEMBER);
        persistMember(project, leaver, ProjectMemberRole.MEMBER);

        List<ProjectMemberSummary> participants = rosterQueryService.getParticipants(project.getId());

        assertThat(participants).extracting(ProjectMemberSummary::userId)
                .containsExactly(owner.getId(), active.getId());
        // OWNER 우선 정렬은 그대로 유지된다.
        assertThat(participants.get(0).userId()).isEqualTo(owner.getId());
    }

    @Test
    @DisplayName("탈퇴한 팀원은 팀 검토 로스터에서 빠진다 — 영구 PENDING 방지")
    void withdrawnMemberIsExcludedFromTeamMembers() {
        User owner = persistUser("팀장", UserStatus.ACTIVE);
        User leaver = persistUser("나간팀원", UserStatus.WITHDRAWN);
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistMember(project, leaver, ProjectMemberRole.MEMBER);

        assertThat(rosterQueryService.getTeamMembers(project.getId())).isEmpty();
    }

    @Test
    @DisplayName("팀원이 모두 탈퇴하면 1인 프로젝트로 센다 — 섹션 확정이 막히지 않도록")
    void withdrawnMemberIsExcludedFromParticipantCount() {
        // countParticipants == 1 이어야 팀원 동의 조건이 면제된다(§6.3.1).
        // 탈퇴자를 세면 승인해 줄 사람이 없는데도 확정이 영영 막힌다.
        User owner = persistUser("팀장", UserStatus.ACTIVE);
        User leaver = persistUser("나간팀원", UserStatus.WITHDRAWN);
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistMember(project, leaver, ProjectMemberRole.MEMBER);

        assertThat(rosterQueryService.countParticipants(project.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("탈퇴자가 없으면 기존과 동일하게 전원을 센다")
    void activeOnlyProjectIsUnchanged() {
        User owner = persistUser("팀장", UserStatus.ACTIVE);
        User member = persistUser("팀원", UserStatus.ACTIVE);
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistMember(project, member, ProjectMemberRole.MEMBER);

        assertThat(rosterQueryService.countParticipants(project.getId())).isEqualTo(2);
        assertThat(rosterQueryService.getParticipants(project.getId())).hasSize(2);
        assertThat(rosterQueryService.getTeamMembers(project.getId())).hasSize(1);
    }

    private User persistUser(String name, UserStatus status) {
        User user = User.builder()
                .name(name)
                .email(name + "-" + System.nanoTime() + "@wevo.com")
                .status(status)
                .build();
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    private Project persistProject(User owner) {
        Project project = Project.builder()
                .owner(owner)
                .title("로스터 검증")
                .ideaText("팀 의견을 모아 하나의 결과물로 만든다.")
                .resultType(OutputType.PROPOSAL)
                .audience("교내 심사위원")
                .status(ProjectStatus.ACTIVE)
                .build();
        entityManager.persist(project);
        entityManager.flush();
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
}
