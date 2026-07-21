package com.wevo.backend.section;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
import com.wevo.backend.section.repository.DraftLeaseRepository;
import com.wevo.backend.section.service.DraftLeaseService;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PostgreSQL의 실제 행 잠금과 Unique 제약을 사용하는 편집 잠금 경합 테스트.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Import(PostgresTestContainerConfig.class)
class DraftLeaseConcurrencyIntegrationTest {

    private static final Duration LOCK_ASSERTION_WINDOW = Duration.ofMillis(300);

    @Autowired
    private DraftLeaseService draftLeaseService;

    @Autowired
    private DraftLeaseRepository draftLeaseRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final TransactionTemplate transactionTemplate;

    @Autowired
    DraftLeaseConcurrencyIntegrationTest(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
    }

    @Test
    void simultaneousAcquisitionCreatesOneLeaseAndRejectsTheOtherRequester() throws Exception {
        LeaseFixture fixture = createFixture(LeaseState.NONE);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<AcquireOutcome> ownerAttempt = executor.submit(
                    acquireTask(fixture.sectionId(), fixture.ownerId(), ready, start));
            Future<AcquireOutcome> memberAttempt = executor.submit(
                    acquireTask(fixture.sectionId(), fixture.memberId(), ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<AcquireOutcome> outcomes = List.of(
                    ownerAttempt.get(5, TimeUnit.SECONDS),
                    memberAttempt.get(5, TimeUnit.SECONDS)
            );
            assertThat(outcomes).filteredOn(AcquireOutcome::acquired).hasSize(1);
            assertThat(outcomes)
                    .filteredOn(outcome -> outcome.errorCode() == ErrorCode.DRAFT_LEASE_HELD_BY_OTHER)
                    .hasSize(1);
        }

        assertThat(draftLeaseRepository.count()).isEqualTo(1);
    }

    @Test
    void renewalWaitsForTheExistingLeasePessimisticLock() throws Exception {
        LeaseFixture fixture = createFixture(LeaseState.ACTIVE);

        withLeaseRowLocked(
                fixture.sectionId(),
                () -> draftLeaseService.renew(fixture.sectionId(), fixture.ownerId())
        );
    }

    @Test
    void expiredLeaseReacquisitionWaitsForTheExistingLeasePessimisticLock() throws Exception {
        LeaseFixture fixture = createFixture(LeaseState.EXPIRED);

        withLeaseRowLocked(
                fixture.sectionId(),
                () -> draftLeaseService.acquire(fixture.sectionId(), fixture.memberId())
        );

        Long holderId = jdbcTemplate.queryForObject(
                "SELECT holder_user_id FROM draft_leases WHERE project_section_id = ?",
                Long.class,
                fixture.sectionId()
        );
        assertThat(holderId).isEqualTo(fixture.memberId());
    }

    private Callable<AcquireOutcome> acquireTask(Long sectionId,
                                                 Long userId,
                                                 CountDownLatch ready,
                                                 CountDownLatch start) {
        return () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("편집 잠금 경합 시작 신호를 받지 못했습니다.");
            }
            try {
                draftLeaseService.acquire(sectionId, userId);
                return AcquireOutcome.acquiredBy(userId);
            } catch (BusinessException exception) {
                return AcquireOutcome.rejected(userId, exception.getErrorCode());
            }
        };
    }

    private void withLeaseRowLocked(Long sectionId, Callable<?> operation) throws Exception {
        CountDownLatch rowLocked = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);

        try (ExecutorService lockExecutor = Executors.newSingleThreadExecutor();
             ExecutorService operationExecutor = Executors.newSingleThreadExecutor()) {
            Future<?> lockHolder = lockExecutor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                draftLeaseRepository.findByProjectSectionIdForUpdate(sectionId).orElseThrow();
                rowLocked.countDown();
                await(releaseLock, "편집 잠금 행 해제 신호를 받지 못했습니다.");
            }));

            assertThat(rowLocked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> blockedOperation = operationExecutor.submit(operation);
            try {
                assertBlocked(blockedOperation);
            } finally {
                releaseLock.countDown();
            }

            blockedOperation.get(5, TimeUnit.SECONDS);
            lockHolder.get(5, TimeUnit.SECONDS);
        }
    }

    private void assertBlocked(Future<?> future) {
        assertThatThrownBy(() -> future.get(LOCK_ASSERTION_WINDOW.toMillis(), TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);
    }

    private LeaseFixture createFixture(LeaseState leaseState) {
        return transactionTemplate.execute(status -> {
            Long ownerId = insertUser("lease-concurrency-owner");
            Long memberId = insertUser("lease-concurrency-member");
            Long projectId = jdbcTemplate.queryForObject(
                    """
                    INSERT INTO projects (owner_user_id, title, result_type, audience, status)
                    VALUES (?, 'lease-concurrency', 'PROPOSAL', 'test-audience', 'ACTIVE')
                    RETURNING id
                    """,
                    Long.class,
                    ownerId
            );
            jdbcTemplate.update(
                    """
                    INSERT INTO project_members (project_id, user_id, role, joined_at)
                    VALUES (?, ?, 'OWNER', CURRENT_TIMESTAMP), (?, ?, 'MEMBER', CURRENT_TIMESTAMP)
                    """,
                    projectId,
                    ownerId,
                    projectId,
                    memberId
            );
            Long sectionId = jdbcTemplate.queryForObject(
                    """
                    INSERT INTO project_sections (project_id, title, section_order, status)
                    VALUES (?, 'lease-concurrency-section', 1, 'DRAFTING')
                    RETURNING id
                    """,
                    Long.class,
                    projectId
            );
            jdbcTemplate.update(
                    "INSERT INTO section_drafts (project_section_id, content, version) VALUES (?, 'draft', 1)",
                    sectionId
            );
            if (leaseState != LeaseState.NONE) {
                String interval = leaseState == LeaseState.ACTIVE ? "5 minutes" : "-1 minute";
                jdbcTemplate.update(
                        """
                        INSERT INTO draft_leases (project_section_id, holder_user_id, lease_until)
                        VALUES (?, ?, CURRENT_TIMESTAMP + CAST(? AS INTERVAL))
                        """,
                        sectionId,
                        ownerId,
                        interval
                );
            }
            return new LeaseFixture(sectionId, ownerId, memberId);
        });
    }

    private Long insertUser(String name) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO users (name, status) VALUES (?, 'ACTIVE') RETURNING id",
                Long.class,
                name
        );
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

    private enum LeaseState {
        NONE,
        ACTIVE,
        EXPIRED
    }

    private record LeaseFixture(Long sectionId, Long ownerId, Long memberId) {
    }

    private record AcquireOutcome(boolean acquired, Long userId, ErrorCode errorCode) {

        static AcquireOutcome acquiredBy(Long userId) {
            return new AcquireOutcome(true, userId, null);
        }

        static AcquireOutcome rejected(Long userId, ErrorCode errorCode) {
            return new AcquireOutcome(false, userId, errorCode);
        }
    }

}
