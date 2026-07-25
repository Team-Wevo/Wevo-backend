package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wevo.backend.ai.domain.AiErrorType;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiRequestStatus;
import com.wevo.backend.ai.dto.response.SynthesisResponse;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
import com.wevo.backend.issue.domain.EvidenceRequest;
import com.wevo.backend.issue.domain.Issue;
import com.wevo.backend.issue.domain.IssueAnswer;
import com.wevo.backend.issue.domain.IssueDecision;
import com.wevo.backend.issue.domain.IssueOption;
import com.wevo.backend.issue.domain.IssueRelatedOpinion;
import com.wevo.backend.issue.domain.IssueStatus;
import com.wevo.backend.issue.domain.IssueType;
import com.wevo.backend.issue.domain.SynthesisInheritedGapAnswer;
import com.wevo.backend.issue.domain.SynthesisSet;
import com.wevo.backend.issue.repository.EvidenceRequestRepository;
import com.wevo.backend.issue.repository.IssueAnswerRepository;
import com.wevo.backend.issue.repository.IssueDecisionRepository;
import com.wevo.backend.issue.repository.IssueOptionRepository;
import com.wevo.backend.issue.repository.IssueRelatedOpinionRepository;
import com.wevo.backend.issue.repository.IssueRepository;
import com.wevo.backend.issue.repository.SynthesisInheritedGapAnswerRepository;
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
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 실제 PostgreSQL에서 합의점·쟁점 조회(§3.8.2)의 세트 필터·유형별 매핑을 검증한다.
 *
 * <p>핵심 관심사는 <b>어느 세트가 현재 세트인가</b>다 — 재정리가 이전 세트를 지우지 않으므로,
 * 조회가 최신 성공 실행의 세트만 노출하고 이전 세대 쟁점·답변은 감추는지 확인한다.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "wevo.ai.jobs.dispatch-enabled=false"
})
@Import(PostgresTestContainerConfig.class)
class SynthesisReadIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 25, 15, 0);

    @Autowired private SynthesisQueryService synthesisQueryService;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectSectionRepository projectSectionRepository;
    @Autowired private OpinionRepository opinionRepository;
    @Autowired private AiJobRepository aiJobRepository;
    @Autowired private SynthesisSetRepository synthesisSetRepository;
    @Autowired private IssueRepository issueRepository;
    @Autowired private IssueOptionRepository issueOptionRepository;
    @Autowired private IssueRelatedOpinionRepository issueRelatedOpinionRepository;
    @Autowired private IssueDecisionRepository issueDecisionRepository;
    @Autowired private EvidenceRequestRepository evidenceRequestRepository;
    @Autowired private IssueAnswerRepository issueAnswerRepository;
    @Autowired private SynthesisInheritedGapAnswerRepository inheritedGapAnswerRepository;

    private User owner;
    private User member;
    private User outsider;
    private Project project;
    private ProjectSection section;
    private Opinion submittedOpinion;

    @BeforeEach
    void setUp() {
        deleteAllFixtures();

        owner = userRepository.save(User.builder()
                .name("윤호").email("owner@wevo.com").status(UserStatus.ACTIVE).build());
        member = userRepository.save(User.builder()
                .name("팀원").email("member@wevo.com").status(UserStatus.ACTIVE).build());
        outsider = userRepository.save(User.builder()
                .name("외부인").email("outsider@wevo.com").status(UserStatus.ACTIVE).build());
        project = projectRepository.save(Project.builder()
                .owner(owner).title("정리 조회 테스트").resultType(OutputType.PROPOSAL)
                .audience("심사위원").status(ProjectStatus.ACTIVE).build());
        projectMemberRepository.save(ProjectMember.builder()
                .project(project).user(owner).role(ProjectMemberRole.OWNER).joinedAt(NOW).build());
        projectMemberRepository.save(ProjectMember.builder()
                .project(project).user(member).role(ProjectMemberRole.MEMBER).joinedAt(NOW).build());
        section = projectSectionRepository.save(ProjectSection.builder()
                .project(project).title("문제 정의").sectionOrder(1)
                .status(ProjectSectionStatus.SYNTHESIZING).build());
        // 쟁점의 관련 의견은 실제 제출 의견을 참조한다 (issue_related_opinions.opinion_id FK).
        Opinion opinion = Opinion.builder()
                .projectSection(section).author(member)
                .content("대학생 팀의 협업 문제를 우선 해결해야 합니다.")
                .status(OpinionStatus.DRAFT).build();
        opinion.submit(NOW.minusHours(1));
        submittedOpinion = opinionRepository.save(opinion);
    }

    /**
     * 이 클래스가 만든 데이터는 <b>끝날 때도 지운다</b> — 트랜잭션 롤백이 없는 통합 테스트라
     * 정리 세트가 남으면 {@code synthesis_sets → ai_jobs} FK 때문에 다음 테스트의
     * {@code aiJobRepository.deleteAll()}이 실패한다.
     */
    @AfterEach
    void tearDown() {
        deleteAllFixtures();
    }

    /** 자식 → 부모 순으로 지운다 (FK 제약 순서). */
    private void deleteAllFixtures() {
        inheritedGapAnswerRepository.deleteAll();
        issueAnswerRepository.deleteAll();
        evidenceRequestRepository.deleteAll();
        issueDecisionRepository.deleteAll();
        issueOptionRepository.deleteAll();
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

    @Test
    @DisplayName("실행 이력이 없는 섹션은 exists=false로 응답한다")
    void noExecution_returnsExistsFalse() {
        SynthesisResponse response = synthesisQueryService.getSynthesis(section.getId(), owner.getId());

        assertThat(response.exists()).isFalse();
        assertThat(response.currentSet()).isNull();
    }

    @Test
    @DisplayName("현재 세트의 CONFLICT 결정·GAP 답변·승계 참조를 유형에 맞게 반환한다")
    void currentSet_mapsIssuesByType() {
        UUID requestId = UUID.randomUUID();
        SynthesisSet set = succeededSet(requestId, 0, "협업 도구 부족이 공통 문제다.", NOW);

        Issue conflict = issueRepository.save(Issue.builder()
                .synthesisSet(set).type(IssueType.CONFLICT)
                .description("우선 사용자층이 갈립니다.")
                .question("어느 사용자층을 우선할까요?").sortOrder(1).build());
        IssueOption first = issueOptionRepository.save(IssueOption.builder()
                .issue(conflict).optionText("대학생").sortOrder(1).build());
        issueOptionRepository.save(IssueOption.builder()
                .issue(conflict).optionText("직장인").sortOrder(2).build());
        issueRelatedOpinionRepository.save(IssueRelatedOpinion.builder()
                .issue(conflict).opinionId(submittedOpinion.getId()).authorUserId(member.getId())
                .authorNameSnapshot("팀원").excerpt("대학생이 주 사용자입니다.").sortOrder(1).build());
        IssueDecision decision = issueDecisionRepository.save(
                IssueDecision.select(conflict, owner.getId(), first, NOW));
        conflict.resolve(decision);
        issueRepository.save(conflict);

        Issue gap = issueRepository.save(Issue.builder()
                .synthesisSet(set).type(IssueType.GAP)
                .description("시장 규모 근거가 없습니다.").sortOrder(2).build());
        EvidenceRequest request = evidenceRequestRepository.save(EvidenceRequest.builder()
                .issue(gap).requestedByUserId(owner.getId()).targetUserId(member.getId())
                .requestedAt(NOW).build());
        IssueAnswer answer = issueAnswerRepository.save(IssueAnswer.builder()
                .evidenceRequest(request).authorUserId(member.getId()).authorNameSnapshot("팀원")
                .content("작년 설문 결과를 근거로 붙입니다.").answeredAt(NOW).build());
        gap.resolve(answer);
        issueRepository.save(gap);

        inheritedGapAnswerRepository.save(SynthesisInheritedGapAnswer.builder()
                .synthesisSet(set).sourceIssueId(gap.getId()).sourceAnswerId(answer.getId()).build());

        SynthesisResponse response = synthesisQueryService.getSynthesis(section.getId(), owner.getId());

        assertThat(response.exists()).isTrue();
        assertThat(response.synthesisStale()).isFalse();
        assertThat(response.currentSet().setId()).isEqualTo(requestId);
        assertThat(response.currentSet().consensusSummary()).isEqualTo("협업 도구 부족이 공통 문제다.");
        assertThat(response.currentSet().issues()).hasSize(2);

        SynthesisResponse.IssueResponse conflictResponse = response.currentSet().issues().get(0);
        assertThat(conflictResponse.issueId()).isEqualTo(conflict.getId());
        assertThat(conflictResponse.type()).isEqualTo(IssueType.CONFLICT);
        assertThat(conflictResponse.status()).isEqualTo(IssueStatus.RESOLVED);
        assertThat(conflictResponse.options()).containsExactly("대학생", "직장인");
        assertThat(conflictResponse.decision().selectedOption()).isEqualTo("대학생");
        assertThat(conflictResponse.decision().customInput()).isNull();
        assertThat(conflictResponse.evidenceRequested()).isNull();
        assertThat(conflictResponse.answer()).isNull();
        assertThat(conflictResponse.relatedOpinions()).hasSize(1);
        assertThat(conflictResponse.relatedOpinions().get(0).authorName()).isEqualTo("팀원");

        SynthesisResponse.IssueResponse gapResponse = response.currentSet().issues().get(1);
        assertThat(gapResponse.type()).isEqualTo(IssueType.GAP);
        assertThat(gapResponse.question()).isNull();
        assertThat(gapResponse.options()).isNull();
        assertThat(gapResponse.decision()).isNull();
        assertThat(gapResponse.evidenceRequested()).isTrue();
        assertThat(gapResponse.answer().content()).isEqualTo("작년 설문 결과를 근거로 붙입니다.");

        assertThat(response.currentSet().inheritedGapAnswers()).hasSize(1);
        assertThat(response.currentSet().inheritedGapAnswers().get(0).answerId())
                .isEqualTo(answer.getId());
    }

    @Test
    @DisplayName("재정리로 세트가 대체되면 최신 성공 세트만 조회되고 이전 세대 쟁점은 감춰진다")
    void supersededSet_isNotReturned() {
        SynthesisSet oldSet = succeededSet(UUID.randomUUID(), 0, "1세대 합의점", NOW.minusHours(2));
        issueRepository.save(Issue.builder()
                .synthesisSet(oldSet).type(IssueType.GAP)
                .description("1세대 쟁점").sortOrder(1).build());

        UUID newRequestId = UUID.randomUUID();
        SynthesisSet newSet = succeededSet(newRequestId, 1, "2세대 합의점", NOW);
        issueRepository.save(Issue.builder()
                .synthesisSet(newSet).type(IssueType.GAP)
                .description("2세대 쟁점").sortOrder(1).build());

        SynthesisResponse response = synthesisQueryService.getSynthesis(section.getId(), owner.getId());

        assertThat(response.currentSet().setId()).isEqualTo(newRequestId);
        assertThat(response.currentSet().consensusSummary()).isEqualTo("2세대 합의점");
        assertThat(response.currentSet().issues())
                .extracting(SynthesisResponse.IssueResponse::description)
                .containsExactly("2세대 쟁점");
    }

    @Test
    @DisplayName("재정리가 실패해도 이전 성공 세트를 유지하고 latestJob만 FAILED로 노출한다")
    void failedRetry_keepsPreviousSetAndReportsFailure() {
        UUID succeededRequestId = UUID.randomUUID();
        succeededSet(succeededRequestId, 0, "1세대 합의점", NOW.minusHours(1));

        AiJob failed = aiJobRepository.save(queuedJob(UUID.randomUUID(), "b".repeat(64), NOW));
        failed.start(NOW);
        failed.fail(AiErrorType.PROVIDER_TIMEOUT, "AI 응답 시간이 초과되었습니다.", NOW);
        aiJobRepository.save(failed);

        SynthesisResponse response = synthesisQueryService.getSynthesis(section.getId(), member.getId());

        assertThat(response.latestJob().requestId()).isEqualTo(failed.getRequestId());
        assertThat(response.latestJob().status()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(response.latestJob().failure().errorCode()).isEqualTo("AI005");
        assertThat(response.latestJob().failure().message()).isEqualTo("AI 응답 시간이 초과되었습니다.");
        assertThat(response.currentSet().setId()).isEqualTo(succeededRequestId);
    }

    @Test
    @DisplayName("작업의 resultId가 다른 세대 세트를 가리키면 DB는 막지 못하고 조회가 실패로 드러낸다")
    void mismatchedResultId_isDetectedAtRead() {
        SynthesisSet older = succeededSet(UUID.randomUUID(), 0, "1세대 합의점", NOW.minusHours(2));

        // 새 작업은 자기 세트를 만들었지만 resultId는 이전 세대 세트를 가리킨다.
        // ai_jobs.result_id에는 FK가 없어 이 상태가 DB 레벨에서 막히지 않는다는 점이 이 검증의 근거다.
        UUID requestId = UUID.randomUUID();
        AiJob job = aiJobRepository.save(queuedJob(requestId, "c".repeat(64), NOW.minusMinutes(1)));
        synthesisSetRepository.save(SynthesisSet.builder()
                .requestId(requestId)
                .projectSectionId(section.getId())
                .opinionGateGeneration(1)
                .consensusSummary("2세대 합의점")
                .build());
        job.start(NOW);
        job.succeed(older.getId(), NOW);
        aiJobRepository.save(job);

        assertThatThrownBy(() -> synthesisQueryService.getSynthesis(section.getId(), owner.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requestId가 일치하지 않습니다");
    }

    @Test
    @DisplayName("팀원(MEMBER)도 조회할 수 있다 (열람은 참여자 전원)")
    void member_canRead() {
        succeededSet(UUID.randomUUID(), 0, "합의점", NOW);

        SynthesisResponse response = synthesisQueryService.getSynthesis(section.getId(), member.getId());

        assertThat(response.exists()).isTrue();
        assertThat(response.currentSet()).isNotNull();
    }

    @Test
    @DisplayName("비멤버가 조회하면 404 S001이다 (존재 숨김)")
    void nonMember_isHidden() {
        succeededSet(UUID.randomUUID(), 0, "합의점", NOW);

        assertThatThrownBy(() -> synthesisQueryService.getSynthesis(section.getId(), outsider.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SECTION_NOT_FOUND));
    }

    /**
     * 성공한 실행 한 건과 그 결과 세트를 함께 만든다. 실제 실행 경로와 같은 연결(작업의
     * {@code resultId} → 세트 id, 세트의 {@code requestId} → 작업의 requestId)을 유지한다.
     */
    private SynthesisSet succeededSet(UUID requestId, long gateGeneration,
                                      String consensusSummary, LocalDateTime completedAt) {
        // synthesis_sets.request_id → ai_jobs.request_id FK가 있어 작업을 먼저 만든다.
        AiJob job = aiJobRepository.save(
                queuedJob(requestId, hashFor(requestId), completedAt.minusMinutes(1)));
        SynthesisSet set = synthesisSetRepository.save(SynthesisSet.builder()
                .requestId(requestId)
                .projectSectionId(section.getId())
                .opinionGateGeneration(gateGeneration)
                .consensusSummary(consensusSummary)
                .build());
        job.start(completedAt);
        job.succeed(set.getId(), completedAt);
        aiJobRepository.save(job);
        return set;
    }

    private AiJob queuedJob(UUID requestId, String idempotencyKey, LocalDateTime queuedAt) {
        return AiJob.queue(
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
                idempotencyKey,
                queuedAt);
    }

    /** 작업마다 다른 멱등키가 필요하므로 requestId에서 64자리 hex를 파생한다. */
    private String hashFor(UUID requestId) {
        return requestId.toString().replace("-", "").repeat(2);
    }
}
