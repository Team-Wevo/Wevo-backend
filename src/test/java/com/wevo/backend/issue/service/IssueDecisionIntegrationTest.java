package com.wevo.backend.issue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
import com.wevo.backend.issue.domain.Issue;
import com.wevo.backend.issue.domain.IssueDecision;
import com.wevo.backend.issue.domain.IssueOption;
import com.wevo.backend.issue.domain.IssueStatus;
import com.wevo.backend.issue.domain.IssueType;
import com.wevo.backend.issue.domain.SynthesisSet;
import com.wevo.backend.issue.dto.request.IssueDecisionRequest;
import com.wevo.backend.issue.dto.response.IssueDecisionResponse;
import com.wevo.backend.issue.repository.IssueDecisionRepository;
import com.wevo.backend.issue.repository.IssueOptionRepository;
import com.wevo.backend.issue.repository.IssueRepository;
import com.wevo.backend.issue.repository.SynthesisSetRepository;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.project.repository.ProjectMemberRepository;
import com.wevo.backend.project.repository.ProjectRepository;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import com.wevo.backend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 실제 PostgreSQL에서 쟁점 결정의 저장·권한·현재 세트·동시성 계약을 검증한다.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "wevo.ai.jobs.dispatch-enabled=false"
})
@Import(PostgresTestContainerConfig.class)
class IssueDecisionIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 28, 12, 0);

    @Autowired private IssueDecisionService issueDecisionService;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectSectionRepository projectSectionRepository;
    @Autowired private AiJobRepository aiJobRepository;
    @Autowired private SynthesisSetRepository synthesisSetRepository;
    @Autowired private IssueRepository issueRepository;
    @Autowired private IssueOptionRepository issueOptionRepository;
    @Autowired private IssueDecisionRepository issueDecisionRepository;

    private User owner;
    private User member;
    private User outsider;
    private Project project;
    private ProjectSection section;

    @BeforeEach
    void setUp() {
        deleteAllFixtures();

        owner = userRepository.save(User.builder()
                .name("윤호").email("decision-owner@wevo.com").status(UserStatus.ACTIVE).build());
        member = userRepository.save(User.builder()
                .name("팀원").email("decision-member@wevo.com").status(UserStatus.ACTIVE).build());
        outsider = userRepository.save(User.builder()
                .name("외부인").email("decision-outsider@wevo.com").status(UserStatus.ACTIVE).build());
        project = projectRepository.save(Project.builder()
                .owner(owner).title("쟁점 결정 테스트").resultType(OutputType.PROPOSAL)
                .audience("심사위원").status(ProjectStatus.ACTIVE).build());
        projectMemberRepository.save(ProjectMember.builder()
                .project(project).user(owner).role(ProjectMemberRole.OWNER).joinedAt(NOW).build());
        projectMemberRepository.save(ProjectMember.builder()
                .project(project).user(member).role(ProjectMemberRole.MEMBER).joinedAt(NOW).build());
        section = projectSectionRepository.save(ProjectSection.builder()
                .project(project).title("문제 정의").sectionOrder(1)
                .status(ProjectSectionStatus.SYNTHESIZING).build());
    }

    @AfterEach
    void tearDown() {
        deleteAllFixtures();
    }

    @Test
    @DisplayName("OWNER가 현재 CONFLICT의 선택지를 결정하면 결정과 RESOLVED 상태가 함께 저장된다")
    void selectedOption_persistsDecisionAndResolution() {
        SynthesisSet set = succeededSet("선택지 결정 세트");
        Issue issue = conflict(set, 1);
        IssueOption option = issueOptionRepository.save(IssueOption.builder()
                .issue(issue).optionText("대학생 팀").sortOrder(1).build());

        IssueDecisionResponse response = issueDecisionService.decide(
                issue.getId(),
                owner.getId(),
                new IssueDecisionRequest("대학생 팀", null));

        Issue reloaded = issueRepository.findById(issue.getId()).orElseThrow();
        IssueDecision decision = issueDecisionRepository
                .findAllWithIssueBySynthesisSetId(set.getId()).getFirst();
        assertThat(response.status()).isEqualTo(IssueStatus.RESOLVED);
        assertThat(reloaded.getStatus()).isEqualTo(IssueStatus.RESOLVED);
        assertThat(decision.getSelectedOption().getId()).isEqualTo(option.getId());
        assertThat(decision.getCustomInput()).isNull();
        assertThat(decision.getDecidedByUserId()).isEqualTo(owner.getId());
        assertThat(decision.getDecidedAt()).isNotNull();
    }

    @Test
    @DisplayName("OWNER가 직접 입력한 결정도 저장할 수 있다")
    void customInput_persistsDecision() {
        SynthesisSet set = succeededSet("직접 입력 결정 세트");
        Issue issue = conflict(set, 1);
        String customInput = "가 ".repeat(IssueDecision.MAX_CUSTOM_INPUT_LENGTH);

        issueDecisionService.decide(
                issue.getId(),
                owner.getId(),
                new IssueDecisionRequest(null, customInput));

        IssueDecision decision = issueDecisionRepository
                .findAllWithIssueBySynthesisSetId(set.getId()).getFirst();
        assertThat(decision.getSelectedOption()).isNull();
        assertThat(decision.getCustomInput()).isEqualTo(customInput);
        assertThat(issueRepository.findById(issue.getId()).orElseThrow().getStatus())
                .isEqualTo(IssueStatus.RESOLVED);
    }

    @Test
    @DisplayName("MEMBER는 A002, 비멤버는 존재를 숨기는 I001로 거부한다")
    void authorization_distinguishesMemberAndOutsider() {
        Issue issue = conflict(succeededSet("권한 검증 세트"), 1);
        IssueDecisionRequest request = new IssueDecisionRequest(null, "직접 결정");

        assertError(
                () -> issueDecisionService.decide(issue.getId(), member.getId(), request),
                ErrorCode.FORBIDDEN);
        assertError(
                () -> issueDecisionService.decide(issue.getId(), outsider.getId(), request),
                ErrorCode.ISSUE_NOT_FOUND);
        assertThat(issueDecisionRepository.findByIssue_Id(issue.getId())).isEmpty();
    }

    @Test
    @DisplayName("재정리로 대체된 이전 세트의 쟁점 결정은 C003으로 거부한다")
    void supersededIssue_returnsC003() {
        SynthesisSet oldSet = succeededSet("이전 세트");
        Issue oldIssue = conflict(oldSet, 1);
        succeededSet("현재 세트");

        assertError(
                () -> issueDecisionService.decide(
                        oldIssue.getId(),
                        owner.getId(),
                        new IssueDecisionRequest(null, "이전 쟁점 결정")),
                ErrorCode.CONFLICT);

        assertThat(issueDecisionRepository.findByIssue_Id(oldIssue.getId())).isEmpty();
        assertThat(issueRepository.findById(oldIssue.getId()).orElseThrow().getStatus())
                .isEqualTo(IssueStatus.PENDING);
    }

    @Test
    @DisplayName("세트 생성 순서와 작업 완료 순서가 달라도 최신 완료 작업의 쟁점만 결정할 수 있다")
    void currentSet_followsLatestSucceededJobResult() {
        SynthesisSet latestByCompletion =
                succeededSet("완료 기준 현재 세트", NOW.plusSeconds(3));
        Issue currentIssue = conflict(latestByCompletion, 1);
        SynthesisSet latestByCreation =
                succeededSet("생성 기준 최신 세트", NOW.plusSeconds(2));
        Issue supersededIssue = conflict(latestByCreation, 1);

        IssueDecisionResponse response = issueDecisionService.decide(
                currentIssue.getId(),
                owner.getId(),
                new IssueDecisionRequest(null, "완료 시각 기준 현재 쟁점 결정"));

        assertThat(response.issueId()).isEqualTo(currentIssue.getId());
        assertError(
                () -> issueDecisionService.decide(
                        supersededIssue.getId(),
                        owner.getId(),
                        new IssueDecisionRequest(null, "생성 시각 기준 쟁점 결정")),
                ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("DB에 미해소 쟁점의 결정이 이미 있으면 유니크 제약을 409 C003으로 변환한다")
    void existingDecisionConstraint_returnsC003() {
        SynthesisSet set = succeededSet("유니크 제약 검증 세트");
        Issue issue = conflict(set, 1);
        issueDecisionRepository.saveAndFlush(
                IssueDecision.custom(issue, owner.getId(), "기존 결정", NOW));

        assertError(
                () -> issueDecisionService.decide(
                        issue.getId(),
                        owner.getId(),
                        new IssueDecisionRequest(null, "중복 결정")),
                ErrorCode.CONFLICT);

        assertThat(issueDecisionRepository.findAll()).hasSize(1);
        assertThat(issueRepository.findById(issue.getId()).orElseThrow().getStatus())
                .isEqualTo(IssueStatus.PENDING);
    }

    @Test
    @DisplayName("동일 쟁점의 동시 결정은 정확히 하나만 성공하고 결정도 하나만 저장된다")
    void concurrentDecision_onlyOneSucceeds() throws Exception {
        Issue issue = conflict(succeededSet("동시 결정 세트"), 1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<DecisionAttempt> first = executor.submit(() ->
                    attemptDecision(issue.getId(), "첫 번째 결정", ready, start));
            Future<DecisionAttempt> second = executor.submit(() ->
                    attemptDecision(issue.getId(), "두 번째 결정", ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<DecisionAttempt> attempts = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS));

            assertThat(attempts).filteredOn(DecisionAttempt::success).hasSize(1);
            assertThat(attempts)
                    .filteredOn(attempt -> !attempt.success())
                    .extracting(DecisionAttempt::errorCode)
                    .containsExactly(ErrorCode.CONFLICT);
            assertThat(issueDecisionRepository.findAll()).hasSize(1);
            assertThat(issueRepository.findById(issue.getId()).orElseThrow().getStatus())
                    .isEqualTo(IssueStatus.RESOLVED);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private DecisionAttempt attemptDecision(
            Long issueId,
            String customInput,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            issueDecisionService.decide(
                    issueId, owner.getId(), new IssueDecisionRequest(null, customInput));
            return new DecisionAttempt(true, null);
        } catch (BusinessException exception) {
            return new DecisionAttempt(false, exception.getErrorCode());
        }
    }

    private Issue conflict(SynthesisSet set, int sortOrder) {
        return issueRepository.save(Issue.builder()
                .synthesisSet(set)
                .type(IssueType.CONFLICT)
                .description("우선 사용자층이 갈립니다.")
                .question("어느 사용자층을 우선할까요?")
                .sortOrder(sortOrder)
                .build());
    }

    private SynthesisSet succeededSet(String summary) {
        return succeededSet(summary, NOW.plusSeconds(1));
    }

    private SynthesisSet succeededSet(String summary, LocalDateTime completedAt) {
        UUID requestId = UUID.randomUUID();
        AiJob job = aiJobRepository.save(AiJob.queue(
                requestId,
                project,
                section,
                owner,
                AiFeature.OPINION_SYNTHESIS,
                "a".repeat(64),
                "source-v1",
                "prompt-v1",
                "schema-v1",
                "model-x",
                4096,
                requestId.toString().replace("-", "").repeat(2),
                completedAt.minusMinutes(1)));
        SynthesisSet set = synthesisSetRepository.save(SynthesisSet.builder()
                .requestId(requestId)
                .projectSectionId(section.getId())
                .opinionGateGeneration(0)
                .consensusSummary(summary)
                .build());
        job.start(completedAt.minusSeconds(1));
        job.succeed(set.getId(), completedAt);
        aiJobRepository.save(job);
        return set;
    }

    private void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()).isEqualTo(expected));
    }

    private void deleteAllFixtures() {
        issueDecisionRepository.deleteAll();
        issueOptionRepository.deleteAll();
        issueRepository.deleteAll();
        synthesisSetRepository.deleteAll();
        aiJobRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectSectionRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    private record DecisionAttempt(boolean success, ErrorCode errorCode) {
    }
}
