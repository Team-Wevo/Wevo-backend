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
import com.wevo.backend.issue.domain.IssueRelatedOpinion;
import com.wevo.backend.issue.domain.IssueStatus;
import com.wevo.backend.issue.domain.IssueType;
import com.wevo.backend.issue.domain.SynthesisSet;
import com.wevo.backend.issue.dto.request.EvidenceRequestCreateRequest;
import com.wevo.backend.issue.dto.response.EvidenceRequestResponse;
import com.wevo.backend.issue.repository.EvidenceRequestRepository;
import com.wevo.backend.issue.repository.IssueRelatedOpinionRepository;
import com.wevo.backend.issue.repository.IssueRepository;
import com.wevo.backend.issue.repository.SynthesisSetRepository;
import com.wevo.backend.opinion.domain.Opinion;
import com.wevo.backend.opinion.domain.OpinionStatus;
import com.wevo.backend.opinion.repository.OpinionRepository;
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
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 실제 PostgreSQL에서 GAP 추가 근거 요청의 권한·현재 세트·동시성 제약을 검증한다.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "wevo.ai.jobs.dispatch-enabled=false"
})
@Import(PostgresTestContainerConfig.class)
class EvidenceRequestIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 28, 12, 0);

    @Autowired private EvidenceRequestService evidenceRequestService;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectSectionRepository projectSectionRepository;
    @Autowired private OpinionRepository opinionRepository;
    @Autowired private AiJobRepository aiJobRepository;
    @Autowired private SynthesisSetRepository synthesisSetRepository;
    @Autowired private IssueRepository issueRepository;
    @Autowired private IssueRelatedOpinionRepository issueRelatedOpinionRepository;
    @Autowired private EvidenceRequestRepository evidenceRequestRepository;

    private User owner;
    private User member;
    private User outsider;
    private Project project;
    private ProjectSection section;
    private Opinion memberOpinion;

    @BeforeEach
    void setUp() {
        deleteAllFixtures();

        owner = userRepository.save(User.builder()
                .name("윤호").email("evidence-owner@wevo.com").status(UserStatus.ACTIVE).build());
        member = userRepository.save(User.builder()
                .name("팀원").email("evidence-member@wevo.com").status(UserStatus.ACTIVE).build());
        outsider = userRepository.save(User.builder()
                .name("외부인").email("evidence-outsider@wevo.com").status(UserStatus.ACTIVE).build());
        project = projectRepository.save(Project.builder()
                .owner(owner).title("추가 근거 요청 테스트").resultType(OutputType.PROPOSAL)
                .audience("심사위원").status(ProjectStatus.ACTIVE).build());
        projectMemberRepository.save(ProjectMember.builder()
                .project(project).user(owner).role(ProjectMemberRole.OWNER).joinedAt(NOW).build());
        projectMemberRepository.save(ProjectMember.builder()
                .project(project).user(member).role(ProjectMemberRole.MEMBER).joinedAt(NOW).build());
        section = projectSectionRepository.save(ProjectSection.builder()
                .project(project).title("문제 정의").sectionOrder(1)
                .status(ProjectSectionStatus.SYNTHESIZING).build());
        memberOpinion = Opinion.builder()
                .projectSection(section)
                .author(member)
                .content("대학생 협업 시장 규모를 뒷받침할 통계 자료가 필요합니다.")
                .status(OpinionStatus.DRAFT)
                .build();
        memberOpinion.submit(NOW.minusMinutes(10));
        opinionRepository.save(memberOpinion);
    }

    @AfterEach
    void tearDown() {
        deleteAllFixtures();
    }

    @Test
    @DisplayName("OWNER가 GAP 관련 의견 작성자에게 추가 근거를 요청하면 요청과 대상 스냅샷이 저장된다")
    void validRequest_persistsEvidenceRequest() {
        Issue gap = gap(succeededSet("근거 요청 세트"), 1);

        EvidenceRequestResponse response = evidenceRequestService.request(
                gap.getId(),
                owner.getId(),
                new EvidenceRequestCreateRequest(member.getId()));

        EvidenceRequest saved = evidenceRequestRepository
                .findByIssue_Id(gap.getId()).orElseThrow();
        assertThat(response.issueId()).isEqualTo(gap.getId());
        assertThat(response.requestedTo().userId()).isEqualTo(member.getId());
        assertThat(response.requestedTo().name()).isEqualTo(member.getName());
        assertThat(saved.getRequestedByUserId()).isEqualTo(owner.getId());
        assertThat(saved.getTargetUserId()).isEqualTo(member.getId());
        assertThat(saved.getRequestedAt()).isNotNull();
        assertThat(issueRepository.findById(gap.getId()).orElseThrow().getStatus())
                .isEqualTo(IssueStatus.PENDING);
    }

    @Test
    @DisplayName("MEMBER는 A002, 비멤버는 존재를 숨기는 I001로 거부한다")
    void authorization_distinguishesMemberAndOutsider() {
        Issue gap = gap(succeededSet("권한 검증 세트"), 1);
        EvidenceRequestCreateRequest request = new EvidenceRequestCreateRequest(member.getId());

        assertError(
                () -> evidenceRequestService.request(gap.getId(), member.getId(), request),
                ErrorCode.FORBIDDEN);
        assertError(
                () -> evidenceRequestService.request(gap.getId(), outsider.getId(), request),
                ErrorCode.ISSUE_NOT_FOUND);
        assertThat(evidenceRequestRepository.findByIssue_Id(gap.getId())).isEmpty();
    }

    @Test
    @DisplayName("관련 의견 작성자가 아닌 사용자를 지정하면 C001로 거부한다")
    void unrelatedTarget_returnsC001() {
        Issue gap = gap(succeededSet("대상 검증 세트"), 1);

        assertError(
                () -> evidenceRequestService.request(
                        gap.getId(),
                        owner.getId(),
                        new EvidenceRequestCreateRequest(owner.getId())),
                ErrorCode.INVALID_INPUT);

        assertThat(evidenceRequestRepository.findByIssue_Id(gap.getId())).isEmpty();
    }

    @Test
    @DisplayName("CONFLICT 쟁점에 추가 근거를 요청하면 C003으로 거부한다")
    void conflictIssue_returnsC003() {
        Issue conflict = conflict(succeededSet("쟁점 유형 검증 세트"), 1);

        assertError(
                () -> evidenceRequestService.request(
                        conflict.getId(),
                        owner.getId(),
                        new EvidenceRequestCreateRequest(member.getId())),
                ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("재정리로 대체된 이전 세트의 GAP에는 추가 근거를 요청할 수 없다")
    void supersededIssue_returnsC003() {
        Issue oldGap = gap(succeededSet("이전 세트"), 1);
        succeededSet("현재 세트");

        assertError(
                () -> evidenceRequestService.request(
                        oldGap.getId(),
                        owner.getId(),
                        new EvidenceRequestCreateRequest(member.getId())),
                ErrorCode.CONFLICT);

        assertThat(evidenceRequestRepository.findByIssue_Id(oldGap.getId())).isEmpty();
    }

    @Test
    @DisplayName("이미 요청한 GAP에 다시 요청하면 I003으로 거부한다")
    void duplicateRequest_returnsI003() {
        Issue gap = gap(succeededSet("중복 요청 세트"), 1);
        EvidenceRequestCreateRequest request = new EvidenceRequestCreateRequest(member.getId());
        evidenceRequestService.request(gap.getId(), owner.getId(), request);

        assertError(
                () -> evidenceRequestService.request(gap.getId(), owner.getId(), request),
                ErrorCode.EVIDENCE_REQUEST_ALREADY_SENT);

        assertThat(evidenceRequestRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("PostgreSQL 유니크 제약 이름을 쟁점 중복 요청 제약으로 식별한다")
    void uniqueConstraintName_isRecognizedFromPostgresException() {
        Issue gap = gap(succeededSet("제약 이름 검증 세트"), 1);
        evidenceRequestRepository.saveAndFlush(EvidenceRequest.builder()
                .issue(gap)
                .requestedByUserId(owner.getId())
                .targetUserId(member.getId())
                .requestedAt(NOW)
                .build());

        assertThatThrownBy(() -> evidenceRequestRepository.saveAndFlush(
                EvidenceRequest.builder()
                        .issue(gap)
                        .requestedByUserId(owner.getId())
                        .targetUserId(member.getId())
                        .requestedAt(NOW.plusMinutes(1))
                        .build()))
                .isInstanceOfSatisfying(
                        DataIntegrityViolationException.class,
                        exception -> assertThat(IssueConstraintViolationMatcher.matches(
                                exception, "uk_evidence_requests_issue")).isTrue());
    }

    @Test
    @DisplayName("동일 GAP의 동시 요청은 하나만 성공하고 나머지는 I003이다")
    void concurrentRequest_onlyOneSucceeds() throws Exception {
        Issue gap = gap(succeededSet("동시 요청 세트"), 1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<RequestAttempt> first = executor.submit(() ->
                    attemptRequest(gap.getId(), ready, start));
            Future<RequestAttempt> second = executor.submit(() ->
                    attemptRequest(gap.getId(), ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<RequestAttempt> attempts = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS));

            assertThat(attempts).filteredOn(RequestAttempt::success).hasSize(1);
            assertThat(attempts)
                    .filteredOn(attempt -> !attempt.success())
                    .extracting(RequestAttempt::errorCode)
                    .containsExactly(ErrorCode.EVIDENCE_REQUEST_ALREADY_SENT);
            assertThat(evidenceRequestRepository.findAll()).hasSize(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private RequestAttempt attemptRequest(
            Long issueId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            evidenceRequestService.request(
                    issueId,
                    owner.getId(),
                    new EvidenceRequestCreateRequest(member.getId()));
            return new RequestAttempt(true, null);
        } catch (BusinessException exception) {
            return new RequestAttempt(false, exception.getErrorCode());
        }
    }

    private Issue gap(SynthesisSet set, int sortOrder) {
        Issue gap = issueRepository.save(Issue.builder()
                .synthesisSet(set)
                .type(IssueType.GAP)
                .description("시장 규모 근거가 부족합니다.")
                .sortOrder(sortOrder)
                .build());
        issueRelatedOpinionRepository.save(IssueRelatedOpinion.builder()
                .issue(gap)
                .opinionId(memberOpinion.getId())
                .authorUserId(member.getId())
                .authorNameSnapshot(member.getName())
                .excerpt(memberOpinion.getSubmittedContentOrLegacy())
                .sortOrder(1)
                .build());
        return gap;
    }

    private Issue conflict(SynthesisSet set, int sortOrder) {
        return issueRepository.save(Issue.builder()
                .synthesisSet(set)
                .type(IssueType.CONFLICT)
                .description("해결 방식이 충돌합니다.")
                .question("어떤 방식을 선택할까요?")
                .sortOrder(sortOrder)
                .build());
    }

    private SynthesisSet succeededSet(String summary) {
        UUID requestId = UUID.randomUUID();
        LocalDateTime completedAt = NOW.plusSeconds(
                aiJobRepository.count() + 1);
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
        evidenceRequestRepository.deleteAll();
        issueRelatedOpinionRepository.deleteAll();
        issueRepository.deleteAll();
        synthesisSetRepository.deleteAll();
        aiJobRepository.deleteAll();
        opinionRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectSectionRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    private record RequestAttempt(boolean success, ErrorCode errorCode) {
    }
}
