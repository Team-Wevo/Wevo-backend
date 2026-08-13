package com.wevo.backend.issue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
import com.wevo.backend.issue.domain.SynthesisSet;
import com.wevo.backend.issue.repository.IssueAnswerRepository;
import com.wevo.backend.issue.repository.IssueDecisionRepository;
import com.wevo.backend.issue.repository.IssueRelatedOpinionRepository;
import com.wevo.backend.issue.repository.IssueRepository;
import com.wevo.backend.issue.repository.SynthesisConsensusEvidenceRepository;
import com.wevo.backend.issue.repository.SynthesisInheritedGapAnswerRepository;
import com.wevo.backend.issue.repository.SynthesisSetRepository;
import com.wevo.backend.project.service.VerifiedSectionAccess;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestContainerConfig.class, SynthesisSetQueryService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SynthesisSetQuerySnapshotIntegrationTest {

    private static final long SECTION_ID = 10L;
    private static final long SET_ID = 20L;

    @Autowired private SynthesisSetQueryService service;
    @Autowired private JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @MockitoBean private SynthesisSetRepository synthesisSetRepository;
    @MockitoBean private IssueAnswerRepository issueAnswerRepository;
    @MockitoBean private SynthesisInheritedGapAnswerRepository inheritedGapAnswerRepository;
    @MockitoBean private IssueRepository issueRepository;
    @MockitoBean private IssueDecisionRepository issueDecisionRepository;
    @MockitoBean private IssueRelatedOpinionRepository issueRelatedOpinionRepository;
    @MockitoBean private SynthesisConsensusEvidenceRepository consensusEvidenceRepository;

    @Autowired
    SynthesisSetQuerySnapshotIntegrationTest(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS synthesis_snapshot_probe "
                + "(id INTEGER PRIMARY KEY, value INTEGER NOT NULL)");
        jdbcTemplate.update("TRUNCATE TABLE synthesis_snapshot_probe");
        jdbcTemplate.update("INSERT INTO synthesis_snapshot_probe (id, value) VALUES (1, 1)");
    }

    @Test
    @DisplayName("다중 쿼리 사이에 동시 커밋이 발생해도 초안 근거 조회는 첫 PostgreSQL snapshot을 유지한다")
    void draftGenerationAssemblyKeepsOneSnapshotAcrossConcurrentCommit() throws Exception {
        CountDownLatch firstQueryRead = new CountDownLatch(1);
        CountDownLatch concurrentCommitCompleted = new CountDownLatch(1);
        AtomicInteger firstValue = new AtomicInteger();
        AtomicInteger laterValue = new AtomicInteger();
        SynthesisSet current = mock(SynthesisSet.class);
        VerifiedSectionAccess access = mock(VerifiedSectionAccess.class);
        given(access.sectionId()).willReturn(SECTION_ID);
        given(current.getId()).willReturn(SET_ID);
        given(current.getProjectSectionId()).willReturn(SECTION_ID);
        given(current.getConsensusSummary()).willReturn("합의");

        given(synthesisSetRepository
                .findTopByProjectSectionIdOrderByCreatedAtDescIdDesc(SECTION_ID))
                .willAnswer(invocation -> {
                    firstValue.set(probeValue());
                    firstQueryRead.countDown();
                    await(concurrentCommitCompleted, "동시 update commit을 기다리지 못했습니다.");
                    return Optional.of(current);
                });
        given(issueAnswerRepository.findAllWithIssueBySynthesisSetId(SET_ID))
                .willAnswer(invocation -> {
                    laterValue.set(probeValue());
                    return List.of();
                });
        given(inheritedGapAnswerRepository.findAllBySynthesisSet_Id(SET_ID))
                .willReturn(List.of());
        given(issueRepository.findAllBySynthesisSet_IdOrderBySortOrderAsc(SET_ID))
                .willReturn(List.of());
        given(issueDecisionRepository.findAllWithIssueBySynthesisSetId(SET_ID))
                .willReturn(List.of());
        given(issueRelatedOpinionRepository.findAllWithIssueBySynthesisSetId(SET_ID))
                .willReturn(List.of());
        given(consensusEvidenceRepository.findAllBySynthesisSet_IdOrderBySortOrderAsc(SET_ID))
                .willReturn(List.of());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var resultFuture = executor.submit(
                    () -> service.getCurrentForDraftGeneration(access));
            assertThat(firstQueryRead.await(5, TimeUnit.SECONDS)).isTrue();

            transactionTemplate.executeWithoutResult(status ->
                    jdbcTemplate.update(
                            "UPDATE synthesis_snapshot_probe SET value = 2 WHERE id = 1"));
            concurrentCommitCompleted.countDown();

            CurrentSynthesisContext result = resultFuture.get(5, TimeUnit.SECONDS);
            assertThat(result.synthesisSetId()).isEqualTo(SET_ID);
        }

        assertThat(firstValue).hasValue(1);
        assertThat(laterValue).hasValue(1);
        assertThat(probeValue()).isEqualTo(2);
    }

    private int probeValue() {
        return jdbcTemplate.queryForObject(
                "SELECT value FROM synthesis_snapshot_probe WHERE id = 1", Integer.class);
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
}
