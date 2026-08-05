package com.wevo.backend.issue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
import com.wevo.backend.issue.domain.EvidenceRequest;
import com.wevo.backend.issue.domain.Issue;
import com.wevo.backend.issue.domain.IssueAnswer;
import com.wevo.backend.issue.domain.IssueStatus;
import com.wevo.backend.issue.domain.IssueType;
import com.wevo.backend.issue.domain.SynthesisSet;
import com.wevo.backend.issue.dto.request.IssueAnswerRequest;
import com.wevo.backend.issue.dto.response.IssueAnswerResponse;
import com.wevo.backend.issue.repository.EvidenceRequestRepository;
import com.wevo.backend.issue.repository.IssueAnswerRepository;
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
import com.wevo.backend.section.domain.SectionDraft;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import com.wevo.backend.section.repository.SectionDraftRepository;
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
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 실제 PostgreSQL에서 GAP 보충 근거 답변의 권한·현재 세트·stale·동시성 제약을 검증한다.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "wevo.ai.jobs.dispatch-enabled=false"
})
@Import(PostgresTestContainerConfig.class)
class IssueAnswerIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 28, 12, 0);
    private static final String CONTENT = "공공데이터포털의 2025년 통계를 보충 근거로 제출합니다.";

    @Autowired private IssueAnswerService issueAnswerService;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectSectionRepository projectSectionRepository;
    @Autowired private SectionDraftRepository sectionDraftRepository;
    @Autowired private AiJobRepository aiJobRepository;
    @Autowired private SynthesisSetRepository synthesisSetRepository;
    @Autowired private IssueRepository issueRepository;
    @Autowired private EvidenceRequestRepository evidenceRequestRepository;
    @Autowired private IssueAnswerRepository issueAnswerRepository;

    private User owner;
    private User member;
    private User outsider;
    private Project project;
    private ProjectSection section;

    @BeforeEach
    void setUp() {
        deleteAllFixtures();

        owner = userRepository.save(User.builder()
                .name("윤호").email("answer-owner@wevo.com").status(UserStatus.ACTIVE).build());
        member = userRepository.save(User.builder()
                .name("팀원").email("answer-member@wevo.com").status(UserStatus.ACTIVE).build());
        outsider = userRepository.save(User.builder()
                .name("외부인").email("answer-outsider@wevo.com").status(UserStatus.ACTIVE).build());
        project = projectRepository.save(Project.builder()
                .owner(owner).title("보충 근거 답변 테스트").resultType(OutputType.PROPOSAL)
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
    @DisplayName("의견 수집 게이트가 닫혀 있어도 지목된 팀원은 답변할 수 있다")
    void targetMember_answersWhileGateClosed() {
        Issue gap = gap(succeededSet("답변 대상 세트"), 1);
        EvidenceRequest request = evidenceRequest(gap);

        IssueAnswerResponse response = issueAnswerService.answer(
                gap.getId(), member.getId(), new IssueAnswerRequest(CONTENT));

        IssueAnswer saved = issueAnswerRepository.findByIssue_Id(gap.getId()).orElseThrow();
        assertThat(response.issueId()).isEqualTo(gap.getId());
        assertThat(response.answerId()).isEqualTo(saved.getId());
        assertThat(response.answeredAt()).isEqualTo(saved.getAnsweredAt());
        assertThat(saved.getEvidenceRequest().getId()).isEqualTo(request.getId());
        assertThat(saved.getAuthorUserId()).isEqualTo(member.getId());
        assertThat(saved.getAuthorNameSnapshot()).isEqualTo(member.getName());
        assertThat(saved.getContent()).isEqualTo(CONTENT);
        assertThat(issueRepository.findById(gap.getId()).orElseThrow().getStatus())
                .isEqualTo(IssueStatus.RESOLVED);
        assertThat(projectSectionRepository.findById(section.getId()).orElseThrow()
                .isSynthesisStale()).isFalse();
    }

    @Test
    @DisplayName("초안 생성 후 답변하면 초안은 유지하고 synthesisStale을 true로 표시한다")
    void answerAfterDraft_marksSynthesisStaleAndKeepsDraft() {
        Issue gap = gap(succeededSet("초안 생성 세트"), 1);
        evidenceRequest(gap);
        section.changeStatus(ProjectSectionStatus.DRAFTING, LocalDateTime.now());
        projectSectionRepository.save(section);
        SectionDraft draft = sectionDraftRepository.save(SectionDraft.builder()
                .projectSection(section)
                .content("기존 초안 본문")
                .version(1)
                .lastEditor(owner)
                .build());

        issueAnswerService.answer(
                gap.getId(), member.getId(), new IssueAnswerRequest(CONTENT));

        assertThat(sectionDraftRepository.findById(draft.getId())).isPresent();
        assertThat(projectSectionRepository.findById(section.getId()).orElseThrow()
                .isSynthesisStale()).isTrue();
    }

    @Test
    @DisplayName("지목되지 않은 참여자는 A002, 비멤버는 존재를 숨기는 I001로 거부한다")
    void authorization_distinguishesWrongTargetAndOutsider() {
        Issue gap = gap(succeededSet("권한 검증 세트"), 1);
        evidenceRequest(gap);
        IssueAnswerRequest request = new IssueAnswerRequest(CONTENT);

        assertError(
                () -> issueAnswerService.answer(gap.getId(), owner.getId(), request),
                ErrorCode.FORBIDDEN);
        assertError(
                () -> issueAnswerService.answer(gap.getId(), outsider.getId(), request),
                ErrorCode.ISSUE_NOT_FOUND);
        assertThat(issueAnswerRepository.findByIssue_Id(gap.getId())).isEmpty();
    }

    @Test
    @DisplayName("근거 요청이 없거나 이미 답변한 GAP은 C003으로 거부한다")
    void missingOrDuplicateAnswer_returnsC003() {
        SynthesisSet set = succeededSet("요청 상태 검증 세트");
        Issue withoutRequest = gap(set, 1);
        Issue requested = gap(set, 2);
        evidenceRequest(requested);

        assertError(
                () -> issueAnswerService.answer(
                        withoutRequest.getId(), member.getId(), new IssueAnswerRequest(CONTENT)),
                ErrorCode.CONFLICT);

        issueAnswerService.answer(
                requested.getId(), member.getId(), new IssueAnswerRequest(CONTENT));
        assertError(
                () -> issueAnswerService.answer(
                        requested.getId(), member.getId(), new IssueAnswerRequest(CONTENT)),
                ErrorCode.CONFLICT);
        assertThat(issueAnswerRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("재정리로 대체된 이전 세트의 GAP에는 답변할 수 없다")
    void supersededIssue_returnsC003() {
        Issue oldGap = gap(succeededSet("이전 세트"), 1);
        evidenceRequest(oldGap);
        succeededSet("현재 세트");

        assertError(
                () -> issueAnswerService.answer(
                        oldGap.getId(), member.getId(), new IssueAnswerRequest(CONTENT)),
                ErrorCode.CONFLICT);
        assertThat(issueAnswerRepository.findByIssue_Id(oldGap.getId())).isEmpty();
    }

    @Test
    @DisplayName("PostgreSQL 답변 유니크 제약 이름을 중복 답변 제약으로 식별한다")
    void uniqueConstraintName_isRecognizedFromPostgresException() {
        Issue gap = gap(succeededSet("제약 이름 검증 세트"), 1);
        EvidenceRequest request = evidenceRequest(gap);
        issueAnswerService.answer(
                gap.getId(), member.getId(), new IssueAnswerRequest(CONTENT));

        assertThatThrownBy(() -> issueAnswerRepository.saveAndFlush(IssueAnswer.builder()
                .evidenceRequest(request)
                .authorUserId(member.getId())
                .authorNameSnapshot(member.getName())
                .content("두 번째 답변")
                .answeredAt(NOW.plusMinutes(1))
                .build()))
                .isInstanceOfSatisfying(
                        DataIntegrityViolationException.class,
                        exception -> assertThat(
                                IssueConstraintViolationMatcher.matches(
                                        exception, "uk_issue_answers_issue")
                                        || IssueConstraintViolationMatcher.matches(
                                        exception, "uk_issue_answers_evidence_request"))
                                .isTrue());
    }

    @Test
    @DisplayName("동일 GAP의 동시 답변은 하나만 성공하고 나머지는 C003이다")
    void concurrentAnswer_onlyOneSucceeds() throws Exception {
        Issue gap = gap(succeededSet("동시 답변 세트"), 1);
        evidenceRequest(gap);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<AnswerAttempt> first = executor.submit(() ->
                    attemptAnswer(gap.getId(), ready, start));
            Future<AnswerAttempt> second = executor.submit(() ->
                    attemptAnswer(gap.getId(), ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<AnswerAttempt> attempts = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS));

            assertThat(attempts).filteredOn(AnswerAttempt::success).hasSize(1);
            assertThat(attempts)
                    .filteredOn(attempt -> !attempt.success())
                    .extracting(AnswerAttempt::errorCode)
                    .containsExactly(ErrorCode.CONFLICT);
            assertThat(issueAnswerRepository.findAll()).hasSize(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private AnswerAttempt attemptAnswer(
            Long issueId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            issueAnswerService.answer(
                    issueId, member.getId(), new IssueAnswerRequest(CONTENT));
            return new AnswerAttempt(true, null);
        } catch (BusinessException exception) {
            return new AnswerAttempt(false, exception.getErrorCode());
        }
    }

    private Issue gap(SynthesisSet set, int sortOrder) {
        return issueRepository.save(Issue.builder()
                .synthesisSet(set)
                .type(IssueType.GAP)
                .description("시장 규모 근거가 부족합니다.")
                .sortOrder(sortOrder)
                .build());
    }

    private EvidenceRequest evidenceRequest(Issue issue) {
        return evidenceRequestRepository.saveAndFlush(EvidenceRequest.builder()
                .issue(issue)
                .requestedByUserId(owner.getId())
                .targetUserId(member.getId())
                .requestedAt(NOW)
                .build());
    }

    private SynthesisSet succeededSet(String summary) {
        UUID requestId = UUID.randomUUID();
        LocalDateTime completedAt = NOW.plusSeconds(aiJobRepository.count() + 1);
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
        issueAnswerRepository.deleteAll();
        evidenceRequestRepository.deleteAll();
        issueRepository.deleteAll();
        synthesisSetRepository.deleteAll();
        aiJobRepository.deleteAll();
        sectionDraftRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectSectionRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    private record AnswerAttempt(boolean success, ErrorCode errorCode) {
    }
}
