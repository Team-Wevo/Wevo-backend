package com.wevo.backend.issue.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.global.config.JpaAuditingConfig;
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
import com.wevo.backend.opinion.domain.Opinion;
import com.wevo.backend.opinion.domain.OpinionStatus;
import com.wevo.backend.opinion.repository.OpinionRepository;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.project.repository.ProjectRepository;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import com.wevo.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, PostgresTestContainerConfig.class})
class SynthesisFoundationIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 22, 14, 0);

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectSectionRepository projectSectionRepository;
    @Autowired
    private OpinionRepository opinionRepository;
    @Autowired
    private AiJobRepository aiJobRepository;
    @Autowired
    private SynthesisSetRepository synthesisSetRepository;
    @Autowired
    private IssueRepository issueRepository;
    @Autowired
    private IssueOptionRepository issueOptionRepository;
    @Autowired
    private IssueRelatedOpinionRepository relatedOpinionRepository;
    @Autowired
    private IssueDecisionRepository decisionRepository;
    @Autowired
    private EvidenceRequestRepository evidenceRequestRepository;
    @Autowired
    private IssueAnswerRepository answerRepository;
    @Autowired
    private SynthesisInheritedGapAnswerRepository inheritedAnswerRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User owner;
    private User member;
    private Project project;
    private ProjectSection section;
    private Opinion submittedOpinion;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(User.builder()
                .name("윤호").email("owner@wevo.com").status(UserStatus.ACTIVE).build());
        member = userRepository.save(User.builder()
                .name("팀원").email("member@wevo.com").status(UserStatus.ACTIVE).build());
        project = projectRepository.save(Project.builder()
                .owner(owner).title("정리 기반 테스트").resultType(OutputType.PROPOSAL)
                .audience("심사위원").status(ProjectStatus.ACTIVE).build());
        section = projectSectionRepository.save(ProjectSection.builder()
                .project(project).title("문제 정의").sectionOrder(1)
                .status(ProjectSectionStatus.SYNTHESIZING).build());
        submittedOpinion = Opinion.builder()
                .projectSection(section).author(member)
                .content("대학생 팀의 협업 문제를 우선 해결해야 합니다.")
                .status(OpinionStatus.DRAFT).build();
        submittedOpinion.submit(NOW.minusHours(1));
        opinionRepository.save(submittedOpinion);
    }

    @Test
    @DisplayName("정리 세트와 쟁점 후속 데이터 및 승계 원본 참조를 저장하고 조회한다")
    void saveAndReadCompleteFoundationGraph() {
        SynthesisSet firstSet = saveSet(0, "대학생 협업 문제에 집중하기로 합의했습니다.");
        Issue conflict = issueRepository.save(Issue.builder()
                .synthesisSet(firstSet).type(IssueType.CONFLICT)
                .description("초기 타깃 범위가 다릅니다.")
                .question("대학생 전체와 공모전 팀 중 어디에 집중할까요?")
                .sortOrder(1).build());
        Issue gap = issueRepository.save(Issue.builder()
                .synthesisSet(firstSet).type(IssueType.GAP)
                .description("시장 규모 근거가 부족합니다.").sortOrder(2).build());
        IssueOption option = issueOptionRepository.save(IssueOption.builder()
                .issue(conflict).optionText("공모전 참가 팀").sortOrder(1).build());
        relatedOpinionRepository.save(IssueRelatedOpinion.builder()
                .issue(gap).opinionId(submittedOpinion.getId()).authorUserId(member.getId())
                .authorNameSnapshot(member.getName()).excerpt(submittedOpinion.getSubmittedContentOrLegacy())
                .sortOrder(1).build());
        IssueDecision decision = decisionRepository.save(
                IssueDecision.select(conflict, owner.getId(), option, NOW));
        conflict.resolve(decision);
        EvidenceRequest request = evidenceRequestRepository.save(EvidenceRequest.builder()
                .issue(gap).requestedByUserId(owner.getId()).targetUserId(member.getId())
                .requestedAt(NOW.plusMinutes(1)).build());
        IssueAnswer answer = answerRepository.save(IssueAnswer.builder()
                .evidenceRequest(request).authorUserId(member.getId()).authorNameSnapshot(member.getName())
                .content("관련 대회 참가 팀은 연간 약 2만 팀입니다.")
                .answeredAt(NOW.plusMinutes(2)).build());
        gap.resolve(answer);
        entityManager.flush();

        SynthesisSet secondSet = saveSet(1, "보충 근거를 반영해 시장 범위를 구체화했습니다.");
        inheritedAnswerRepository.saveAndFlush(SynthesisInheritedGapAnswer.builder()
                .synthesisSet(secondSet).sourceIssueId(gap.getId()).sourceAnswerId(answer.getId()).build());
        entityManager.clear();

        List<Issue> issues = issueRepository.findAllBySynthesisSet_IdOrderBySortOrderAsc(firstSet.getId());
        assertThat(issues).extracting(Issue::getType)
                .containsExactly(IssueType.CONFLICT, IssueType.GAP);
        assertThat(issues).extracting(Issue::getStatus)
                .containsExactly(IssueStatus.RESOLVED, IssueStatus.RESOLVED);
        assertThat(inheritedAnswerRepository.findAllBySynthesisSet_Id(secondSet.getId()))
                .singleElement().satisfies(reference -> {
                    assertThat(reference.getSourceIssueId()).isEqualTo(gap.getId());
                    assertThat(reference.getSourceAnswerId()).isEqualTo(answer.getId());
                });
    }

    @Test
    @DisplayName("V3가 의견 마감 세대 CHECK 제약의 기존 행 검증을 완료한다")
    void migration_validatesOpinionGateGenerationConstraint() {
        Boolean validated = jdbcTemplate.queryForObject(
                """
                SELECT convalidated
                FROM pg_constraint
                WHERE conname = 'chk_project_sections_opinion_gate_generation'
                """,
                Boolean.class
        );

        assertThat(validated).isTrue();
    }

    @Test
    @DisplayName("관련 의견 발췌문은 의견 작업본이 바뀌어도 생성 당시 값으로 보존된다")
    void relatedOpinion_preservesExcerptSnapshot() {
        SynthesisSet set = saveSet(0, "합의 요약");
        Issue gap = issueRepository.save(Issue.builder()
                .synthesisSet(set).type(IssueType.GAP).description("근거 부족").sortOrder(1).build());
        String originalExcerpt = submittedOpinion.getSubmittedContentOrLegacy();
        IssueRelatedOpinion related = relatedOpinionRepository.saveAndFlush(IssueRelatedOpinion.builder()
                .issue(gap).opinionId(submittedOpinion.getId()).authorUserId(member.getId())
                .authorNameSnapshot(member.getName()).excerpt(originalExcerpt).sortOrder(1).build());

        submittedOpinion.updateContent("재오픈 이후 완전히 달라진 작업본입니다.");
        opinionRepository.flush();
        entityManager.clear();

        assertThat(relatedOpinionRepository.findById(related.getId()).orElseThrow().getExcerpt())
                .isEqualTo(originalExcerpt);
    }

    @Test
    @DisplayName("한 GAP 쟁점에는 추가 근거 요청을 한 번만 저장할 수 있다")
    void evidenceRequest_isUniquePerIssue() {
        SynthesisSet set = saveSet(0, "합의 요약");
        Issue gap = issueRepository.save(Issue.builder()
                .synthesisSet(set).type(IssueType.GAP).description("근거 부족").sortOrder(1).build());
        evidenceRequestRepository.saveAndFlush(EvidenceRequest.builder()
                .issue(gap).requestedByUserId(owner.getId()).targetUserId(member.getId())
                .requestedAt(NOW).build());

        assertThatThrownBy(() -> evidenceRequestRepository.saveAndFlush(EvidenceRequest.builder()
                .issue(gap).requestedByUserId(owner.getId()).targetUserId(member.getId())
                .requestedAt(NOW.plusMinutes(1)).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("결정은 다른 CONFLICT 쟁점의 선택지를 DB에서도 참조할 수 없다")
    void decision_rejectsOptionFromDifferentIssueAtDatabaseBoundary() {
        SynthesisSet set = saveSet(0, "합의 요약");
        Issue first = issueRepository.save(Issue.builder()
                .synthesisSet(set).type(IssueType.CONFLICT).description("첫 번째 충돌")
                .question("첫 번째 질문").sortOrder(1).build());
        Issue second = issueRepository.save(Issue.builder()
                .synthesisSet(set).type(IssueType.CONFLICT).description("두 번째 충돌")
                .question("두 번째 질문").sortOrder(2).build());
        IssueOption firstOption = issueOptionRepository.saveAndFlush(IssueOption.builder()
                .issue(first).optionText("첫 번째 쟁점 선택지").sortOrder(1).build());

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO issue_decisions (
                    issue_id, decided_by_user_id, selected_option_id, decided_at
                ) VALUES (?, ?, ?, ?)
                """,
                second.getId(), owner.getId(), firstOption.getId(), NOW
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private SynthesisSet saveSet(long gateGeneration, String summary) {
        UUID requestId = UUID.randomUUID();
        AiJob job = AiJob.queue(
                requestId, project, section, owner, AiFeature.OPINION_SYNTHESIS,
                "a".repeat(64), "gate-" + gateGeneration, "v1", "v1", "test-model",
                1000, UUID.randomUUID().toString().replace("-", "") + "b".repeat(32), NOW
        );
        aiJobRepository.saveAndFlush(job);
        job.start(NOW.plusSeconds(1));
        SynthesisSet set = synthesisSetRepository.saveAndFlush(SynthesisSet.builder()
                .requestId(requestId).projectSectionId(section.getId())
                .opinionGateGeneration(gateGeneration).consensusSummary(summary).build());
        job.succeed(set.getId(), NOW.plusSeconds(2));
        aiJobRepository.flush();
        return set;
    }
}
