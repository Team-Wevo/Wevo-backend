package com.wevo.backend.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
import com.wevo.backend.review.domain.ReviewLinkStatus;
import com.wevo.backend.review.service.ReviewLinkService;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
 * PostgreSQL의 실제 행 잠금을 사용하는 외부 검토 링크 상태 전이 경합 테스트.
 *
 * <p>링크 상태를 바꾸는 경로는 잠그는 행이 서로 다르다 — 본문 저장의 만료 처리는 섹션 행을,
 * 팀장의 수동 종료는 링크 행을 잡는다. 섹션 잠금만으로는 둘이 직렬화되지 않으므로,
 * 만료 처리가 만료 대상을 <b>잠금 없이</b> 읽으면 방금 커밋된 종료를 덮어쓰는 변경 손실이 난다.
 * ("종료 상태는 되돌아가지 않는다" — {@code ReviewLink} 클래스 주석, API_SPEC §3.5.9 멱등 규칙)
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Import(PostgresTestContainerConfig.class)
class ReviewLinkConcurrencyIntegrationTest {

    private static final Duration LOCK_ASSERTION_WINDOW = Duration.ofMillis(300);

    @Autowired
    private ReviewLinkService reviewLinkService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final TransactionTemplate transactionTemplate;

    @Autowired
    ReviewLinkConcurrencyIntegrationTest(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
    }

    @Test
    @DisplayName("수동 종료 커밋 직후의 본문 수정 만료 처리는 CLOSED 를 OUTDATED 로 덮지 않는다")
    void draftSaveExpiryDoesNotOverwriteConcurrentManualClose() throws Exception {
        LinkFixture fixture = createFixture();
        CountDownLatch closeApplied = new CountDownLatch(1);
        CountDownLatch releaseClose = new CountDownLatch(1);

        try (ExecutorService closeExecutor = Executors.newSingleThreadExecutor();
             ExecutorService expireExecutor = Executors.newSingleThreadExecutor()) {

            // 팀장의 수동 종료가 링크 행 잠금을 잡은 채 커밋 직전에 머문다.
            Future<?> closeTransaction = closeExecutor.submit(
                    () -> transactionTemplate.executeWithoutResult(status -> {
                        reviewLinkService.updateStatus(
                                fixture.linkId(), fixture.ownerId(), ReviewLinkStatus.CLOSED);
                        closeApplied.countDown();
                        await(releaseClose, "수동 종료 해제 신호를 받지 못했습니다.");
                    }));

            assertThat(closeApplied.await(5, TimeUnit.SECONDS)).isTrue();

            // 그 사이 본문이 저장돼 만료 처리가 돈다. 만료 대상을 잠금 없이 읽으면 여기서 ACTIVE 를
            // 보고 통과해 버린다 — 종료가 커밋될 때까지 대기해야 한다.
            Future<?> expireTransaction = expireExecutor.submit(
                    () -> reviewLinkService.markSectionLinksOutdated(fixture.sectionId()));
            try {
                assertBlocked(expireTransaction);
            } finally {
                releaseClose.countDown();
            }

            expireTransaction.get(5, TimeUnit.SECONDS);
            closeTransaction.get(5, TimeUnit.SECONDS);
        }

        // 잠금 없이 읽었다면 낡은 ACTIVE 위에서 markOutdated() 가드를 통과해 OUTDATED 가 된다.
        assertThat(statusOf(fixture.linkId())).isEqualTo(ReviewLinkStatus.CLOSED.name());
    }

    private void assertBlocked(Future<?> future) {
        assertThatThrownBy(() -> future.get(LOCK_ASSERTION_WINDOW.toMillis(), TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);
    }

    private String statusOf(Long linkId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM review_links WHERE id = ?", String.class, linkId);
    }

    private LinkFixture createFixture() {
        return transactionTemplate.execute(status -> {
            Long ownerId = jdbcTemplate.queryForObject(
                    "INSERT INTO users (name, status) VALUES ('review-link-concurrency-owner', 'ACTIVE') "
                            + "RETURNING id",
                    Long.class
            );
            Long projectId = jdbcTemplate.queryForObject(
                    """
                    INSERT INTO projects (owner_user_id, title, result_type, audience, status)
                    VALUES (?, 'review-link-concurrency', 'PROPOSAL', 'test-audience', 'ACTIVE')
                    RETURNING id
                    """,
                    Long.class,
                    ownerId
            );
            jdbcTemplate.update(
                    """
                    INSERT INTO project_members (project_id, user_id, role, joined_at)
                    VALUES (?, ?, 'OWNER', CURRENT_TIMESTAMP)
                    """,
                    projectId,
                    ownerId
            );
            Long sectionId = jdbcTemplate.queryForObject(
                    """
                    INSERT INTO project_sections (project_id, title, section_order, status)
                    VALUES (?, 'review-link-concurrency-section', 1, 'REVIEWING')
                    RETURNING id
                    """,
                    Long.class,
                    projectId
            );
            jdbcTemplate.update(
                    "INSERT INTO section_drafts (project_section_id, content, version) VALUES (?, '본문', 1)",
                    sectionId
            );
            Long linkId = jdbcTemplate.queryForObject(
                    """
                    INSERT INTO review_links (project_section_id, created_by_user_id, token_hash,
                                              section_title_snapshot, content_snapshot, content_version, status)
                    VALUES (?, ?, ?, 'review-link-concurrency-section', '본문', 1, 'ACTIVE')
                    RETURNING id
                    """,
                    Long.class,
                    sectionId,
                    ownerId,
                    "a".repeat(64)
            );
            return new LinkFixture(sectionId, linkId, ownerId);
        });
    }

    private void await(CountDownLatch latch, String message) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(message);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(message, exception);
        }
    }

    private record LinkFixture(Long sectionId, Long linkId, Long ownerId) {
    }
}
