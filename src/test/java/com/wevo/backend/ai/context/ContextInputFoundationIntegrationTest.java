package com.wevo.backend.ai.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wevo.backend.issue.domain.EvidenceRequest;
import com.wevo.backend.issue.domain.Issue;
import com.wevo.backend.issue.domain.IssueAnswer;
import com.wevo.backend.issue.domain.IssueType;
import com.wevo.backend.issue.domain.SynthesisInheritedGapAnswer;
import com.wevo.backend.issue.domain.SynthesisSet;
import com.wevo.backend.issue.repository.EvidenceRequestRepository;
import com.wevo.backend.issue.repository.IssueAnswerRepository;
import com.wevo.backend.issue.repository.IssueRepository;
import com.wevo.backend.issue.repository.SynthesisInheritedGapAnswerRepository;
import com.wevo.backend.issue.repository.SynthesisSetRepository;
import com.wevo.backend.issue.service.CurrentSynthesisContext;
import com.wevo.backend.issue.service.SynthesisSetQueryService;
import com.wevo.backend.opinion.domain.Opinion;
import com.wevo.backend.opinion.domain.OpinionStatus;
import com.wevo.backend.opinion.repository.OpinionRepository;
import com.wevo.backend.opinion.service.SubmittedOpinionContext;
import com.wevo.backend.opinion.service.SubmittedOpinionQueryService;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.project.repository.ProjectMemberRepository;
import com.wevo.backend.project.repository.ProjectRepository;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.ProjectAiContext;
import com.wevo.backend.project.service.ProjectAiContextQueryService;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.domain.SectionDraft;
import com.wevo.backend.section.domain.SectionTemplate;
import com.wevo.backend.section.domain.TemplateDependencyType;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import com.wevo.backend.section.repository.SectionDraftRepository;
import com.wevo.backend.section.repository.SectionTemplateRepository;
import com.wevo.backend.section.repository.TemplateDependencyRepository;
import com.wevo.backend.section.seed.SectionTemplateSeeder;
import com.wevo.backend.section.service.PrerequisiteSectionContent;
import com.wevo.backend.section.service.SectionAiContextQueryService;
import com.wevo.backend.section.service.SectionAiMetadata;
import com.wevo.backend.section.service.SectionDependencyReference;
import com.wevo.backend.section.service.SectionVersionedContent;
import com.wevo.backend.section.service.TemplateDependencyQueryService;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import com.wevo.backend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * DB 고유 동작이 아닌 소유 도메인 read contract 조립을 검증하는 경량 통합 테스트.
 * dependency seed/쿼리의 PostgreSQL 계약은 별도 Testcontainers 테스트에서 검증한다.
 */
@SpringBootTest
@Transactional
class ContextInputFoundationIntegrationTest {

    private static final LocalDateTime SUBMITTED_AT =
            LocalDateTime.of(2026, 7, 24, 10, 0);

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectMemberRepository projectMemberRepository;
    @Autowired
    private SectionTemplateRepository sectionTemplateRepository;
    @Autowired
    private ProjectSectionRepository projectSectionRepository;
    @Autowired
    private SectionDraftRepository sectionDraftRepository;
    @Autowired
    private TemplateDependencyRepository templateDependencyRepository;
    @Autowired
    private OpinionRepository opinionRepository;
    @Autowired
    private SynthesisSetRepository synthesisSetRepository;
    @Autowired
    private IssueRepository issueRepository;
    @Autowired
    private EvidenceRequestRepository evidenceRequestRepository;
    @Autowired
    private IssueAnswerRepository issueAnswerRepository;
    @Autowired
    private SynthesisInheritedGapAnswerRepository inheritedGapAnswerRepository;
    @Autowired
    private SectionTemplateSeeder sectionTemplateSeeder;
    @Autowired
    private ProjectAccessGuard projectAccessGuard;
    @Autowired
    private ProjectAiContextQueryService projectQueryService;
    @Autowired
    private SectionAccessGuard sectionAccessGuard;
    @Autowired
    private TemplateDependencyQueryService dependencyQueryService;
    @Autowired
    private SectionAiContextQueryService sectionQueryService;
    @Autowired
    private SubmittedOpinionQueryService opinionQueryService;
    @Autowired
    private SynthesisSetQueryService synthesisQueryService;

    private User owner;
    private User memberOne;
    private User memberTwo;
    private Project project;
    private VerifiedProjectAccess access;
    private List<ProjectSection> sections;

    @BeforeEach
    void setUp() {
        owner = user("owner@context.test", "팀장");
        memberOne = user("one@context.test", "팀원1");
        memberTwo = user("two@context.test", "팀원2");
        project = projectRepository.save(Project.builder()
                .owner(owner)
                .title("AI context 기반")
                .description(" ")
                .ideaText("팀 의견을 안정적으로 조립한다.")
                .resultType(OutputType.PRESENTATION)
                .audience("심사위원")
                .status(ProjectStatus.ACTIVE)
                .build());
        projectMemberRepository.save(ProjectMember.builder()
                .project(project)
                .user(owner)
                .role(ProjectMemberRole.OWNER)
                .joinedAt(SUBMITTED_AT.minusDays(1))
                .build());
        access = projectAccessGuard.requireParticipantAccess(project.getId(), owner.getId());

        sections = sectionTemplateRepository.findByResultTypeOrderByOrderNo(OutputType.PRESENTATION)
                .stream()
                .map(template -> projectSectionRepository.save(ProjectSection.builder()
                        .project(project)
                        .template(template)
                        .title(template.getTitle())
                        .sectionOrder(template.getOrderNo())
                        .status(ProjectSectionStatus.DRAFTING)
                        .build()))
                .toList();
    }

    @Test
    void dependencySeedIsIdempotentAndDirectQueriesMatchPolicy() throws Exception {
        sectionTemplateSeeder.run(null);

        assertThat(templateDependencyRepository.findAllWithTemplates()).hasSize(16);
        assertThat(templateDependencyRepository.findAllWithTemplates())
                .filteredOn(dependency ->
                        dependency.getFromTemplate().getResultType() == OutputType.PRESENTATION)
                .hasSize(9)
                .allMatch(dependency ->
                        dependency.getDependencyType() == TemplateDependencyType.REQUIRES);
        assertThat(templateDependencyRepository.findAllWithTemplates())
                .filteredOn(dependency ->
                        dependency.getFromTemplate().getResultType() == OutputType.PROPOSAL)
                .hasSize(7);

        ProjectSection solution = section("solution-direction");
        assertThat(dependencyQueryService.findDirectPrerequisites(access, solution.getId()))
                .extracting(SectionDependencyReference::templateKey)
                .containsExactly("problem-definition", "target-user");

        ProjectSection problem = section("problem-definition");
        assertThat(dependencyQueryService.findDirectDependents(access, problem.getId()))
                .extracting(SectionDependencyReference::templateKey)
                .containsExactly("target-user", "solution-direction", "expected-impact");
    }

    @Test
    void projectAndSectionContractsExposeOnlyImmutableAiInputValues() {
        ProjectAiContext projectContext = projectQueryService.getProjectContext(access);
        SectionAiMetadata sectionContext =
                sectionQueryService.getMetadata(access, section("problem-definition").getId());

        assertThat(projectContext.description()).isNull();
        assertThat(ProjectAiContext.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly(
                        "projectId", "title", "description", "ideaText", "audience", "outputType")
                .doesNotContain("owner", "members", "email");
        assertThat(SectionAiMetadata.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly(
                        "sectionId",
                        "projectId",
                        "title",
                        "sectionOrder",
                        "status",
                        "opinionGateGeneration",
                        "synthesisStale",
                        "templateKey",
                        "templateDescription",
                        "templateGuide");
    }

    @Test
    void submittedOpinionReadUsesSubmittedCopyAndStableTieBreakWithoutPersonalData() {
        Opinion first = submitted(memberOne, "첫 번째 제출본은 변경 전 내용입니다.");
        Opinion second = submitted(memberTwo, "두 번째 제출본은 그대로 유지됩니다.");
        first.updateContent("제출 후 바꾼 작업본은 AI 입력에서 제외됩니다.");
        opinionRepository.save(Opinion.builder()
                .projectSection(section("problem-definition"))
                .author(owner)
                .content("제출하지 않은 작업본도 제외됩니다.")
                .status(OpinionStatus.DRAFT)
                .build());

        List<SubmittedOpinionContext> result = opinionQueryService.findSubmittedOpinions(
                access, section("problem-definition").getId());

        assertThat(result).extracting(SubmittedOpinionContext::opinionId)
                .containsExactly(first.getId(), second.getId());
        assertThat(result).extracting(SubmittedOpinionContext::submittedContent)
                .containsExactly(
                        "첫 번째 제출본은 변경 전 내용입니다.",
                        "두 번째 제출본은 그대로 유지됩니다.");
        assertThat(SubmittedOpinionContext.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("opinionId", "submittedContent", "submittedAt", "authorReference")
                .doesNotContain("email", "name", "profileImageUrl");
    }

    @Test
    void latestAndConfirmedDraftsRemainDistinctAndProjectMismatchFails() {
        ProjectSection problem = section("problem-definition");
        ProjectSection confirmedProblem = replaceWithConfirmed(problem);
        sectionDraftRepository.saveAll(List.of(
                draft(confirmedProblem, 1, "확정된 문제 정의 본문"),
                draft(confirmedProblem, 2, "확정 이후 편집된 최신 본문")));

        SectionVersionedContent confirmed =
                sectionQueryService.getConfirmedDraft(access, confirmedProblem.getId());
        SectionVersionedContent latest =
                sectionQueryService.getLatestDraft(access, confirmedProblem.getId());
        List<PrerequisiteSectionContent> latestParents =
                sectionQueryService.findDirectLatestPrerequisites(
                        access, section("target-user").getId());
        List<PrerequisiteSectionContent> confirmedParents =
                sectionQueryService.findDirectConfirmedPrerequisites(
                        access, section("target-user").getId());

        assertThat(confirmed.contentVersion()).isEqualTo(1);
        assertThat(latest.contentVersion()).isEqualTo(2);
        assertThat(latestParents).singleElement()
                .extracting(PrerequisiteSectionContent::contentVersion).isEqualTo(2);
        assertThat(confirmedParents).singleElement()
                .extracting(PrerequisiteSectionContent::contentVersion).isEqualTo(1);

        Project other = projectRepository.save(Project.builder()
                .owner(owner)
                .title("다른 프로젝트")
                .resultType(OutputType.PRESENTATION)
                .audience("다른 대상")
                .status(ProjectStatus.ACTIVE)
                .build());
        ProjectSection foreign = projectSectionRepository.save(ProjectSection.builder()
                .project(other)
                .template(template("problem-definition"))
                .title("다른 문제 정의")
                .sectionOrder(1)
                .status(ProjectSectionStatus.DRAFTING)
                .build());

        assertThatThrownBy(() -> sectionQueryService.getMetadata(access, foreign.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("소속");
    }

    @Test
    void currentSynthesisCombinesDirectAndInheritedGapAnswersOnceInStableOrder() {
        ProjectSection target = section("problem-definition");
        SynthesisSet previous = synthesisSetRepository.saveAndFlush(synthesis(target, 0, "이전 정리"));
        Issue oldGap = issueRepository.save(gap(previous, 1, "이전 근거가 부족합니다."));
        IssueAnswer oldAnswer = answer(oldGap, memberOne, "이전 세트 보충 답변", SUBMITTED_AT);

        SynthesisSet current = synthesisSetRepository.saveAndFlush(synthesis(target, 1, "현재 정리"));
        Issue currentGap = issueRepository.save(gap(current, 1, "현재 근거가 부족합니다."));
        IssueAnswer directAnswer =
                answer(currentGap, memberTwo, "현재 세트 직접 답변", SUBMITTED_AT.plusHours(1));
        inheritedGapAnswerRepository.save(SynthesisInheritedGapAnswer.builder()
                .synthesisSet(current)
                .sourceIssueId(oldGap.getId())
                .sourceAnswerId(oldAnswer.getId())
                .build());
        inheritedGapAnswerRepository.save(SynthesisInheritedGapAnswer.builder()
                .synthesisSet(current)
                .sourceIssueId(currentGap.getId())
                .sourceAnswerId(directAnswer.getId())
                .build());

        CurrentSynthesisContext result =
                synthesisQueryService.getCurrentForAiContext(
                        sectionAccessGuard.verifySectionAccess(access, target.getId()));

        assertThat(result.synthesisSetId()).isEqualTo(current.getId());
        assertThat(result.gapAnswers()).extracting(answer -> answer.answerId())
                .containsExactly(oldAnswer.getId(), directAnswer.getId());
        assertThat(result.gapAnswers()).extracting(answer -> answer.content())
                .containsExactly("이전 세트 보충 답변", "현재 세트 직접 답변");
    }

    @Test
    void currentSynthesisRejectsInheritedAnswerFromAnotherSection() {
        ProjectSection target = section("problem-definition");
        SynthesisSet foreignSet = synthesisSetRepository.saveAndFlush(
                synthesis(section("target-user"), 0, "다른 섹션 정리"));
        Issue foreignGap = issueRepository.save(gap(foreignSet, 1, "다른 섹션 GAP"));
        IssueAnswer foreignAnswer =
                answer(foreignGap, memberOne, "다른 섹션의 답변", SUBMITTED_AT);

        SynthesisSet current =
                synthesisSetRepository.saveAndFlush(synthesis(target, 0, "현재 정리"));
        inheritedGapAnswerRepository.save(SynthesisInheritedGapAnswer.builder()
                .synthesisSet(current)
                .sourceIssueId(foreignGap.getId())
                .sourceAnswerId(foreignAnswer.getId())
                .build());

        assertThatThrownBy(() -> synthesisQueryService.getCurrentForAiContext(
                sectionAccessGuard.verifySectionAccess(access, target.getId())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("무결성");
    }

    private User user(String email, String name) {
        return userRepository.save(User.builder()
                .name(name)
                .email(email)
                .status(UserStatus.ACTIVE)
                .build());
    }

    private Opinion submitted(User author, String content) {
        Opinion opinion = Opinion.builder()
                .projectSection(section("problem-definition"))
                .author(author)
                .content(content)
                .status(OpinionStatus.DRAFT)
                .build();
        opinion.submit(SUBMITTED_AT);
        return opinionRepository.saveAndFlush(opinion);
    }

    private ProjectSection replaceWithConfirmed(ProjectSection original) {
        projectSectionRepository.delete(original);
        projectSectionRepository.flush();
        ProjectSection confirmed = ProjectSection.builder()
                .project(project)
                .template(original.getTemplate())
                .title(original.getTitle())
                .sectionOrder(original.getSectionOrder())
                .status(ProjectSectionStatus.CONFIRMED)
                .confirmedVersion(1)
                .build();
        ProjectSection saved = projectSectionRepository.saveAndFlush(confirmed);
        sections = sections.stream()
                .map(section -> section == original ? saved : section)
                .toList();
        return saved;
    }

    private SectionDraft draft(ProjectSection section, int version, String content) {
        return SectionDraft.builder()
                .projectSection(section)
                .version(version)
                .content(content)
                .lastEditor(owner)
                .build();
    }

    private SynthesisSet synthesis(
            ProjectSection section, long generation, String summary
    ) {
        return SynthesisSet.builder()
                .requestId(UUID.randomUUID())
                .projectSectionId(section.getId())
                .opinionGateGeneration(generation)
                .consensusSummary(summary)
                .build();
    }

    private Issue gap(SynthesisSet set, int order, String description) {
        return Issue.builder()
                .synthesisSet(set)
                .type(IssueType.GAP)
                .description(description)
                .sortOrder(order)
                .build();
    }

    private IssueAnswer answer(
            Issue issue, User author, String content, LocalDateTime answeredAt
    ) {
        EvidenceRequest request = evidenceRequestRepository.save(EvidenceRequest.builder()
                .issue(issue)
                .requestedByUserId(owner.getId())
                .targetUserId(author.getId())
                .requestedAt(answeredAt.minusMinutes(5))
                .build());
        return issueAnswerRepository.saveAndFlush(IssueAnswer.builder()
                .evidenceRequest(request)
                .authorUserId(author.getId())
                .authorNameSnapshot(author.getName())
                .content(content)
                .answeredAt(answeredAt)
                .build());
    }

    private ProjectSection section(String templateKey) {
        return sections.stream()
                .filter(section -> section.getTemplate().getSectionKey().equals(templateKey))
                .findFirst()
                .orElseThrow();
    }

    private SectionTemplate template(String templateKey) {
        return sectionTemplateRepository.findByResultTypeOrderByOrderNo(OutputType.PRESENTATION)
                .stream()
                .filter(template -> template.getSectionKey().equals(templateKey))
                .findFirst()
                .orElseThrow();
    }
}
