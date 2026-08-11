package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wevo.backend.ai.domain.AiErrorType;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.project.repository.ProjectRepository;
import com.wevo.backend.review.domain.ReviewIntentComparison;
import com.wevo.backend.review.domain.ReviewIntentComparisonStatus;
import com.wevo.backend.review.domain.ReviewLink;
import com.wevo.backend.review.domain.ReviewLinkStatus;
import com.wevo.backend.review.domain.ReviewSubmission;
import com.wevo.backend.review.domain.UnderstandingSignal;
import com.wevo.backend.review.repository.ReviewIntentComparisonRepository;
import com.wevo.backend.review.repository.ReviewLinkRepository;
import com.wevo.backend.review.repository.ReviewSubmissionRepository;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import com.wevo.backend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "wevo.ai.jobs.dispatch-enabled=false"
})
@Import(PostgresTestContainerConfig.class)
class ReviewIntentComparisonRecoveryIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired private ReviewIntentComparisonRecoveryService recoveryService;
    @Autowired private AiJobService aiJobService;
    @Autowired private AiJobRepository aiJobRepository;
    @Autowired private ReviewIntentComparisonRepository comparisonRepository;
    @Autowired private ReviewSubmissionRepository submissionRepository;
    @Autowired private ReviewLinkRepository reviewLinkRepository;
    @Autowired private ProjectSectionRepository sectionRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void cleanUp() {
        comparisonRepository.deleteAll();
        submissionRepository.deleteAll();
        reviewLinkRepository.deleteAll();
        aiJobRepository.deleteAll();
        sectionRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterEach
    void cleanUpAfterTest() {
        cleanUp();
    }

    @Test
    void pendingComparisonWithoutJobIsRecoveredAfterEventLossGrace() {
        Fixture fixture = fixture(false);
        LocalDateTime threshold = LocalDateTime.now(KST).plusMinutes(1);

        assertThat(recoveryService.recoverPendingComparisons(threshold, 20)).isEqualTo(1);

        assertThat(comparisonRepository.findById(fixture.comparisonId())).get()
                .satisfies(comparison -> {
                    assertThat(comparison.getStatus())
                            .isEqualTo(ReviewIntentComparisonStatus.FAILED);
                    assertThat(comparison.getFailureCode()).isEqualTo("AI999");
                });
    }

    @Test
    void heartbeatRecoveryAlsoTerminatesLinkedPendingComparison() {
        Fixture fixture = fixture(true);
        LocalDateTime threshold = LocalDateTime.now(KST).plusMinutes(1);

        assertThat(aiJobService.recoverIfHeartbeatStale(fixture.requestId(), threshold)).isTrue();
        assertThat(recoveryService.recoverPendingComparisons(threshold, 20)).isEqualTo(1);

        assertThat(aiJobRepository.findByRequestId(fixture.requestId())).get()
                .satisfies(job -> {
                    assertThat(job.getStatus()).isEqualTo(AiJobStatus.FAILED);
                    assertThat(job.getFinalErrorType())
                            .isEqualTo(AiErrorType.WORKER_HEARTBEAT_TIMEOUT);
                });
        assertThat(comparisonRepository.findById(fixture.comparisonId())).get()
                .satisfies(comparison -> {
                    assertThat(comparison.getStatus())
                            .isEqualTo(ReviewIntentComparisonStatus.FAILED);
                    assertThat(comparison.getFailureCode()).isEqualTo("AI999");
                    assertThat(comparison.getSourceAiJobId()).isEqualTo(fixture.sourceAiJobId());
                });
        assertThat(comparisonRepository.findBySourceAiJobId(fixture.sourceAiJobId())).isPresent();
    }

    private Fixture fixture(boolean withRunningJob) {
        User owner = userRepository.save(User.builder()
                .name("팀장")
                .email(UUID.randomUUID() + "@wevo.com")
                .status(UserStatus.ACTIVE)
                .build());
        Project project = projectRepository.save(Project.builder()
                .owner(owner)
                .title("검토 비교 회수")
                .resultType(OutputType.PROPOSAL)
                .audience("검토자")
                .status(ProjectStatus.ACTIVE)
                .build());
        ProjectSection section = sectionRepository.save(ProjectSection.builder()
                .project(project)
                .title("문제 정의")
                .sectionOrder(1)
                .status(ProjectSectionStatus.REVIEWING)
                .build());
        ReviewLink link = reviewLinkRepository.save(ReviewLink.builder()
                .projectSection(section)
                .createdBy(owner)
                .tokenHash(UUID.randomUUID().toString().replace("-", ""))
                .sectionTitleSnapshot(section.getTitle())
                .contentSnapshot("검토할 본문")
                .contentVersion(1)
                .status(ReviewLinkStatus.ACTIVE)
                .build());
        ReviewSubmission submission = submissionRepository.save(ReviewSubmission.builder()
                .reviewLink(link)
                .anonymousReviewerId(UUID.randomUUID().toString())
                .understandingSignal(UnderstandingSignal.PARTIAL)
                .summary("검토자가 이해한 내용")
                .build());
        ReviewIntentComparison comparison = ReviewIntentComparison.pending(
                submission,
                "a".repeat(64),
                "b".repeat(64),
                ReviewIntentComparisonContract.PROMPT_VERSION,
                ReviewIntentComparisonContract.SCHEMA_VERSION,
                "model");

        UUID requestId = null;
        Long sourceAiJobId = null;
        if (withRunningJob) {
            LocalDateTime staleAt = LocalDateTime.now(KST).minusMinutes(10);
            AiJob job = AiJob.queue(
                    UUID.randomUUID(),
                    project,
                    section,
                    owner,
                    AiFeature.REVIEW_INTENT_COMPARISON,
                    "c".repeat(64),
                    "review-submission-" + submission.getId() + "-draft-v1",
                    ReviewIntentComparisonContract.PROMPT_VERSION,
                    ReviewIntentComparisonContract.SCHEMA_VERSION,
                    "model",
                    1_000,
                    "d".repeat(64),
                    staleAt);
            job.start(staleAt);
            aiJobRepository.saveAndFlush(job);
            comparison.bindSourceJobId(job.getId());
            requestId = job.getRequestId();
            sourceAiJobId = job.getId();
        }
        comparisonRepository.saveAndFlush(comparison);
        return new Fixture(comparison.getId(), requestId, sourceAiJobId);
    }

    private record Fixture(Long comparisonId, UUID requestId, Long sourceAiJobId) {
    }
}
