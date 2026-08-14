package com.wevo.backend.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.project.dto.response.InviteLinkResponse;
import com.wevo.backend.project.repository.InviteLinkRepository;
import com.wevo.backend.project.repository.ProjectMemberRepository;
import com.wevo.backend.project.service.InviteService;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 보관(삭제)된 프로젝트에 대한 초대 링크 재발급·참여가 실제 PostgreSQL 에서 차단되는지 검증한다. (#280)
 *
 * <p>{@code joinByToken} 의 방어는 <b>프로젝트 행을 {@code PESSIMISTIC_WRITE} 로 잠근 뒤</b> 보관 여부를
 * 재확인하는 것이다. 이 재확인은 링크가 아직 활성인데 프로젝트만 보관된 TOCTOU 창을 겨냥하므로, 프로젝트
 * 행만 직접 {@code ARCHIVED} 로 만들고 초대 링크는 활성으로 남겨 그 가드를 격리 검증한다. (정상 경로의
 * {@code archiveProject} 는 링크까지 비활성화하므로 사용하면 활성 링크 조회 단계에서 단락되어 잠금 후
 * 재확인 경로가 실행되지 않는다.) 실제 잠금·스키마·제약은 실 DB 로만 검증된다. ({@code CLAUDE.md §8})
 *
 * <p>클래스 트랜잭션을 두지 않는다 — 준비·보관·참여가 각자 커밋해 실제 격리 수준에서 동작을 재현한다.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Import(PostgresTestContainerConfig.class)
class InviteArchivedProjectIntegrationTest {

    @Autowired
    private InviteService inviteService;
    @Autowired
    private InviteLinkRepository inviteLinkRepository;
    @Autowired
    private ProjectMemberRepository projectMemberRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @PersistenceContext
    private EntityManager em;

    private final TransactionTemplate transactionTemplate;

    @Autowired
    InviteArchivedProjectIntegrationTest(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
    }

    @Test
    @DisplayName("잠금 후 프로젝트가 보관 상태면 참여를 거부하고 멤버를 저장하지 않는다 (TOCTOU)")
    void joinByToken_projectArchivedAfterLinkIssued_rejectsAndPersistsNoMember() {
        Fixture fixture = createFixture();
        // 활성 링크를 먼저 발급한다(프로젝트가 아직 ACTIVE 인 시점).
        InviteLinkResponse link =
                inviteService.createInviteLink(fixture.ownerId(), fixture.projectId());
        Long joinerId = persistUser("합류희망자", "joiner@wevo.com");
        // 링크는 활성으로 둔 채 프로젝트만 보관한다 — 잠금 후 재확인 가드가 실행되는 창을 재현.
        archiveProjectRowOnly(fixture.projectId());

        assertThatThrownBy(() -> inviteService.joinByToken(joinerId, link.token()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVITE_LINK_NOT_FOUND));

        assertThat(projectMemberRepository.findByProjectIdAndUserId(fixture.projectId(), joinerId))
                .isEmpty();
    }

    @Test
    @DisplayName("보관된 프로젝트면 초대 링크를 재발급하지 않고 PROJECT_NOT_FOUND (존재 숨김)")
    void createInviteLink_projectArchived_rejectsAndPersistsNoLink() {
        Fixture fixture = createFixture();
        archiveProjectRowOnly(fixture.projectId());

        assertThatThrownBy(() -> inviteService.createInviteLink(fixture.ownerId(), fixture.projectId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PROJECT_NOT_FOUND));

        assertThat(inviteLinkRepository.findAllByProjectIdAndIsActiveTrue(fixture.projectId()))
                .isEmpty();
    }

    /** 프로젝트 행만 ARCHIVED 로 만든다 — 초대 링크 활성 상태는 건드리지 않는다. */
    private void archiveProjectRowOnly(Long projectId) {
        int updated = jdbcTemplate.update(
                "UPDATE projects SET status = ? WHERE id = ?",
                ProjectStatus.ARCHIVED.name(), projectId);
        assertThat(updated).isEqualTo(1);
    }

    private Long persistUser(String name, String email) {
        return transactionTemplate.execute(status -> {
            User user = User.builder()
                    .name(name).email(email)
                    .status(UserStatus.ACTIVE).build();
            em.persist(user);
            em.flush();
            return user.getId();
        });
    }

    private record Fixture(Long ownerId, Long projectId) {
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            User owner = User.builder()
                    .name("팀장").email("owner@wevo.com")
                    .status(UserStatus.ACTIVE).build();
            em.persist(owner);
            Project project = Project.builder()
                    .owner(owner).title("보관 초대 차단 검증")
                    .resultType(OutputType.PROPOSAL).audience("교내 심사위원")
                    .status(ProjectStatus.ACTIVE).build();
            em.persist(project);
            em.persist(ProjectMember.builder()
                    .project(project).user(owner).role(ProjectMemberRole.OWNER)
                    .joinedAt(LocalDateTime.now()).build());
            em.flush();
            return new Fixture(owner.getId(), project.getId());
        });
    }
}
