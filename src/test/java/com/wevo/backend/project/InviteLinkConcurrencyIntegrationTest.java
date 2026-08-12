package com.wevo.backend.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.project.dto.response.InviteLinkResponse;
import com.wevo.backend.project.repository.InviteLinkRepository;
import com.wevo.backend.project.service.InviteService;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
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
 * 초대 링크 최초 생성이 동시에 들어와도 멱등하게 성공하는지 실제 PostgreSQL 로 검증한다. (#232)
 *
 * <p>토큰이 projectId 파생이라 결정적이므로, 링크가 아직 없는 프로젝트에서 동시 생성 요청이 각자
 * {@code save} 를 시도하면 늦은 쪽이 {@code uk_invite_links_token_hash} 유니크 제약에서 터진다.
 * 프로젝트 행 배타 잠금으로 직렬화하는 동작은 실제 DB 잠금·제약으로만 검증된다. ({@code CLAUDE.md §8})
 *
 * <p>클래스 트랜잭션을 두지 않는다 — 각 스레드가 자기 트랜잭션으로 커밋해야 경합이 실제로 재현된다.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Import(PostgresTestContainerConfig.class)
class InviteLinkConcurrencyIntegrationTest {

    private static final int CONCURRENCY = 8;

    @Autowired
    private InviteService inviteService;
    @Autowired
    private InviteLinkRepository inviteLinkRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @PersistenceContext
    private EntityManager em;

    private final TransactionTemplate transactionTemplate;

    @Autowired
    InviteLinkConcurrencyIntegrationTest(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
    }

    @Test
    @DisplayName("동시 최초 생성 — 전부 성공하고 활성 링크는 하나만 남으며 토큰이 모두 같다")
    void concurrentFirstCreation_isIdempotent() throws Exception {
        Fixture fixture = createFixture();

        CyclicBarrier barrier = new CyclicBarrier(CONCURRENCY);
        List<InviteLinkResponse> responses;
        try (var executor = Executors.newFixedThreadPool(CONCURRENCY)) {
            List<Callable<InviteLinkResponse>> tasks = IntStream.range(0, CONCURRENCY)
                    .<Callable<InviteLinkResponse>>mapToObj(index -> () -> {
                        barrier.await();
                        return inviteService.createInviteLink(fixture.ownerId(), fixture.projectId());
                    })
                    .toList();
            responses = executor.invokeAll(tasks).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new AssertionError("동시 최초 생성은 모두 성공해야 한다", exception);
                        }
                    })
                    .toList();
        }

        assertThat(responses).hasSize(CONCURRENCY).doesNotContainNull();
        // 토큰은 projectId 파생이라 모든 응답이 같은 값이어야 한다.
        assertThat(responses).extracting(InviteLinkResponse::token)
                .containsOnly(responses.getFirst().token());
        // 경합해도 활성 링크는 하나뿐이다.
        assertThat(inviteLinkRepository.findAllByProjectIdAndIsActiveTrue(fixture.projectId()))
                .hasSize(1);
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
                    .owner(owner).title("초대 경합 검증")
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
