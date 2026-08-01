package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "wevo.ai.jobs.dispatch-enabled=false"
})
@Import(PostgresTestContainerConfig.class)
class AuthorIntentConfirmationIntegrationTest {

    @Autowired private AuthorIntentConfirmationService confirmationService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final TransactionTemplate transactionTemplate;

    @Autowired
    AuthorIntentConfirmationIntegrationTest(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
    }

    @Test
    void concurrentConfirmationSerializesToOneIntentForTheDraftVersion() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<String> first = executor.submit(() -> confirm(
                    fixture, "첫 번째 확정 의도다.", ready, start));
            Future<String> second = executor.submit(() -> confirm(
                    fixture, "두 번째 확정 의도다.", ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("첫 번째 확정 의도다.", "두 번째 확정 의도다.");
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM section_author_intents "
                        + "WHERE project_section_id = ? AND content_version = 1",
                Integer.class,
                fixture.sectionId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT confirmed_intent FROM section_author_intents "
                        + "WHERE project_section_id = ? AND content_version = 1",
                String.class,
                fixture.sectionId()))
                .isIn("첫 번째 확정 의도다.", "두 번째 확정 의도다.");
    }

    private String confirm(
            Fixture fixture,
            String intent,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("동시 확정 시작 신호를 받지 못했습니다.");
        }
        return confirmationService.confirm(
                fixture.sectionId(), fixture.ownerId(), 1, intent).confirmedIntent();
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            Long ownerId = jdbcTemplate.queryForObject(
                    "INSERT INTO users (name, status) VALUES ('intent-owner', 'ACTIVE') RETURNING id",
                    Long.class);
            Long projectId = jdbcTemplate.queryForObject(
                    """
                    INSERT INTO projects (owner_user_id, title, result_type, audience, status)
                    VALUES (?, 'intent-project', 'PROPOSAL', 'test-audience', 'ACTIVE')
                    RETURNING id
                    """,
                    Long.class,
                    ownerId);
            jdbcTemplate.update(
                    """
                    INSERT INTO project_members (project_id, user_id, role, joined_at)
                    VALUES (?, ?, 'OWNER', CURRENT_TIMESTAMP)
                    """,
                    projectId,
                    ownerId);
            Long sectionId = jdbcTemplate.queryForObject(
                    """
                    INSERT INTO project_sections (project_id, title, section_order, status)
                    VALUES (?, 'intent-section', 1, 'DRAFTING')
                    RETURNING id
                    """,
                    Long.class,
                    projectId);
            jdbcTemplate.update(
                    """
                    INSERT INTO section_drafts (project_section_id, content, version)
                    VALUES (?, '현재 초안이다.', 1)
                    """,
                    sectionId);
            return new Fixture(sectionId, ownerId);
        });
    }

    private record Fixture(Long sectionId, Long ownerId) {
    }
}
