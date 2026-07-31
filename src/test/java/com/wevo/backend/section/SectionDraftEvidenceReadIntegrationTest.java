package com.wevo.backend.section;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.service.DraftGenerationContract;
import com.wevo.backend.ai.service.SynthesisContract;
import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.issue.domain.EvidenceRequest;
import com.wevo.backend.issue.domain.Issue;
import com.wevo.backend.issue.domain.IssueAnswer;
import com.wevo.backend.issue.domain.IssueDecision;
import com.wevo.backend.issue.domain.IssueType;
import com.wevo.backend.issue.domain.SynthesisSet;
import com.wevo.backend.opinion.domain.Opinion;
import com.wevo.backend.opinion.domain.OpinionStatus;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.domain.SectionDraft;
import com.wevo.backend.section.service.AiSectionDraftCreateCommand;
import com.wevo.backend.section.service.AiSectionDraftCreateCommand.DecisionEvidence;
import com.wevo.backend.section.service.AiSectionDraftCreateCommand.GapAnswerEvidence;
import com.wevo.backend.section.service.AiSectionDraftCreateCommand.OpinionEvidence;
import com.wevo.backend.section.service.AiSectionDraftWriter;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 초안 근거 보기 전 경로를 실제 PostgreSQL 컨텍스트로 검증한다. (API_SPEC §3.7.3)
 *
 * <p>근거 행은 FK로 synthesis_sets · ai_jobs · opinions · issues · issue_answers를 물기 때문에,
 * 직접 insert하는 대신 실제 생성 경로({@link AiSectionDraftWriter#createAiDraft})로 만든다
 * ({@link AiSectionDraftWriterIntegrationTest}의 fixture 구성을 그대로 따름).
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Import(PostgresTestContainerConfig.class)
@AutoConfigureMockMvc
@Transactional
class SectionDraftEvidenceReadIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AiSectionDraftWriter writer;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("최신 AI 초안의 근거(의견·결정·GAP 답변)를 반환한다")
    void returnsLatestDraftEvidence() throws Exception {
        Fixture fixture = fixture();
        writer.createAiDraft(command(fixture));
        em.flush();
        em.clear();

        getEvidence(fixture.section().getId(), fixture.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.consensusSummary").value("생성 시점 합의"))
                .andExpect(jsonPath("$.data.opinions[0].opinionId").value(fixture.opinion().getId()))
                .andExpect(jsonPath("$.data.opinions[0].content").value("제출 의견 스냅샷"))
                .andExpect(jsonPath("$.data.decisions[0].issueId").value(fixture.conflict().getId()))
                .andExpect(jsonPath("$.data.decisions[0].decision").value("OWNER 확정 결정"))
                .andExpect(jsonPath("$.data.gapAnswers[0].issueId").value(fixture.gap().getId()))
                .andExpect(jsonPath("$.data.gapAnswers[0].answerId").value(fixture.answer().getId()))
                .andExpect(jsonPath("$.data.gapAnswers[0].inherited").value(true));
    }

    @Test
    @DisplayName("AI 초안이 여러 번 생성되면 가장 높은 버전의 근거만 반환한다")
    void returnsEvidenceOfHighestVersionAiDraftOnly() throws Exception {
        Fixture fixture = fixture();
        writer.createAiDraft(command(fixture, 0, fixture.generationRequestId(),
                "이전 세트 합의", "이전 초안이 쓴 의견 스냅샷", "이전 결정"));

        // 재수집(COLLECTING) → 재정리(SYNTHESIZING)를 거쳐 같은 섹션에 두 번째 AI 초안(version 2)을 만든다.
        // AI 초안 생성은 SYNTHESIZING 에서만 가능하고 DRAFTING → SYNTHESIZING 직접 전이는 막혀 있다.
        ProjectSection section = em.find(ProjectSection.class, fixture.section().getId());
        section.changeStatus(ProjectSectionStatus.COLLECTING);
        section.changeStatus(ProjectSectionStatus.SYNTHESIZING);
        UUID secondRequestId = UUID.randomUUID();
        em.persist(job(secondRequestId, section.getProject(), section, fixture.owner(),
                AiFeature.DRAFT_GENERATION, randomHex64()));
        em.flush();

        writer.createAiDraft(command(fixture, 1, secondRequestId,
                "최신 세트 합의", "최신 초안이 쓴 의견 스냅샷", "최신 결정"));
        em.flush();
        em.clear();

        // 근거 행이 둘 다 남아 있어도(생성 시점 스냅샷 보존) 조회는 최신 버전 것만 내려준다.
        getEvidence(fixture.section().getId(), fixture.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.consensusSummary").value("최신 세트 합의"))
                .andExpect(jsonPath("$.data.opinions[0].content").value("최신 초안이 쓴 의견 스냅샷"))
                .andExpect(jsonPath("$.data.decisions[0].decision").value("최신 결정"));
    }

    @Test
    @DisplayName("AI로 생성된 초안이 없으면 404(S003)")
    void noAiDraftEvidenceReturnsNotFound() throws Exception {
        User owner = persistUser("owner-evidence-none@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        ProjectSection section = persistSection(project, ProjectSectionStatus.DRAFTING);
        // 직접 작성한 초안만 있고 AI 생성 근거는 없는 경우
        em.persist(SectionDraft.builder()
                .projectSection(section)
                .content("직접 쓴 본문")
                .version(1)
                .lastEditor(owner)
                .build());
        em.flush();

        getEvidence(section.getId(), owner)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("S003"));
    }

    @Test
    @DisplayName("프로젝트 참여자가 아니면 404(S001) — 섹션 존재를 숨긴다")
    void nonMemberHiddenAsNotFound() throws Exception {
        Fixture fixture = fixture();
        writer.createAiDraft(command(fixture));
        User outsider = persistUser("outsider-evidence@wevo.com");
        em.flush();
        em.clear();

        getEvidence(fixture.section().getId(), outsider)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("S001"));
    }

    @Test
    @DisplayName("존재하지 않는 섹션을 조회하면 404(S001)")
    void unknownSection() throws Exception {
        User user = persistUser("user-evidence-unknown@wevo.com");
        em.flush();

        getEvidence(999_999L, user)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("S001"));
    }

    // --- 헬퍼 (AiSectionDraftWriterIntegrationTest의 fixture 구성과 동일) ---

    private ResultActions getEvidence(Long sectionId, User user) throws Exception {
        return mockMvc.perform(get("/api/project-sections/{id}/draft/evidence", sectionId)
                .with(authentication(authOf(user))));
    }

    private UsernamePasswordAuthenticationToken authOf(User user) {
        return new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(user.getId()), null, AuthorityUtils.NO_AUTHORITIES);
    }

    private User persistUser(String email) {
        User user = User.builder().name(email.substring(0, email.indexOf('@')))
                .email(email).status(UserStatus.ACTIVE).build();
        em.persist(user);
        return user;
    }

    private Project persistProject(User owner) {
        Project project = Project.builder()
                .owner(owner)
                .title("위보 기획")
                .resultType(OutputType.PRESENTATION)
                .audience("프로젝트 팀원")
                .status(ProjectStatus.ACTIVE)
                .build();
        em.persist(project);
        return project;
    }

    private ProjectMember persistMember(Project project, User user, ProjectMemberRole role) {
        ProjectMember member = ProjectMember.builder()
                .project(project).user(user).role(role).joinedAt(LocalDateTime.now(KST)).build();
        em.persist(member);
        return member;
    }

    private ProjectSection persistSection(Project project, ProjectSectionStatus status) {
        ProjectSection section = ProjectSection.builder()
                .project(project)
                .title("문제 정의")
                .sectionOrder(1)
                .status(status)
                .build();
        em.persist(section);
        return section;
    }

    private AiSectionDraftCreateCommand command(Fixture fixture) {
        return command(fixture, 0, fixture.generationRequestId(),
                "생성 시점 합의", "제출 의견 스냅샷", "OWNER 확정 결정");
    }

    /**
     * 근거 스냅샷 문구를 호출마다 다르게 주어, 어느 초안의 근거가 반환됐는지 구분할 수 있게 한다.
     */
    private AiSectionDraftCreateCommand command(
            Fixture fixture,
            int baseVersion,
            UUID generationRequestId,
            String consensusSummary,
            String opinionSnapshot,
            String decisionSnapshot
    ) {
        return new AiSectionDraftCreateCommand(
                fixture.section().getId(),
                fixture.owner().getId(),
                "AI가 생성한 본문 v" + (baseVersion + 1),
                baseVersion,
                fixture.synthesisSet().getId(),
                0,
                generationRequestId,
                "a".repeat(64),
                "draft-v1-s" + fixture.synthesisSet().getId() + "-g0",
                consensusSummary,
                List.of(new OpinionEvidence(
                        fixture.opinion().getId(), fixture.owner().getName(), opinionSnapshot)),
                List.of(new DecisionEvidence(
                        fixture.conflict().getId(),
                        fixture.decision().getId(),
                        fixture.conflict().getQuestion(),
                        decisionSnapshot)),
                List.of(new GapAnswerEvidence(
                        fixture.gap().getId(),
                        fixture.answer().getId(),
                        fixture.owner().getName(),
                        fixture.answer().getContent(),
                        fixture.answer().getAnsweredAt(),
                        true)),
                List.of()
        );
    }

    private Fixture fixture() {
        User owner = persistUser("owner-evidence-" + UUID.randomUUID() + "@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        ProjectSection section = persistSection(project, ProjectSectionStatus.SYNTHESIZING);
        em.flush();

        UUID synthesisRequestId = UUID.randomUUID();
        em.persist(job(synthesisRequestId, project, section, owner,
                AiFeature.OPINION_SYNTHESIS, randomHex64()));
        SynthesisSet set = SynthesisSet.builder()
                .requestId(synthesisRequestId)
                .projectSectionId(section.getId())
                .opinionGateGeneration(0)
                .consensusSummary("생성 시점 합의")
                .build();
        em.persist(set);

        Opinion opinion = Opinion.builder()
                .projectSection(section)
                .author(owner)
                .content("제출 가능한 길이를 충족하는 합성 의견입니다.")
                .status(OpinionStatus.DRAFT)
                .build();
        opinion.submit(LocalDateTime.now(KST));
        em.persist(opinion);

        Issue conflict = Issue.builder()
                .synthesisSet(set)
                .type(IssueType.CONFLICT)
                .description("충돌")
                .question("어떤 결정을 반영할까요?")
                .sortOrder(1)
                .build();
        em.persist(conflict);
        IssueDecision decision = IssueDecision.custom(
                conflict, owner.getId(), "OWNER 확정 결정", LocalDateTime.now(KST));
        em.persist(decision);
        conflict.resolve(decision);

        Issue gap = Issue.builder()
                .synthesisSet(set)
                .type(IssueType.GAP)
                .description("근거 공백")
                .sortOrder(2)
                .build();
        em.persist(gap);
        EvidenceRequest request = EvidenceRequest.builder()
                .issue(gap)
                .requestedByUserId(owner.getId())
                .targetUserId(owner.getId())
                .requestedAt(LocalDateTime.now(KST))
                .build();
        em.persist(request);
        IssueAnswer answer = IssueAnswer.builder()
                .evidenceRequest(request)
                .authorUserId(owner.getId())
                .authorNameSnapshot(owner.getName())
                .content("확인된 보충 근거")
                .answeredAt(LocalDateTime.now(KST))
                .build();
        em.persist(answer);
        gap.resolve(answer);

        UUID generationRequestId = UUID.randomUUID();
        em.persist(job(generationRequestId, project, section, owner,
                AiFeature.DRAFT_GENERATION, randomHex64()));
        em.flush();

        return new Fixture(owner, section, set, opinion, conflict, decision, gap, answer, generationRequestId);
    }

    private String randomHex64() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    private AiJob job(
            UUID requestId,
            Project project,
            ProjectSection section,
            User owner,
            AiFeature feature,
            String idempotencyKey
    ) {
        return AiJob.queue(
                requestId,
                project,
                section,
                owner,
                feature,
                "a".repeat(64),
                feature == AiFeature.DRAFT_GENERATION ? "draft-v1-s1-g0" : "source",
                feature == AiFeature.DRAFT_GENERATION
                        ? DraftGenerationContract.PROMPT_VERSION
                        : SynthesisContract.PROMPT_VERSION,
                feature == AiFeature.DRAFT_GENERATION
                        ? DraftGenerationContract.SCHEMA_VERSION
                        : SynthesisContract.SCHEMA_VERSION,
                "model",
                1_000,
                idempotencyKey,
                LocalDateTime.now(KST)
        );
    }

    private record Fixture(
            User owner,
            ProjectSection section,
            SynthesisSet synthesisSet,
            Opinion opinion,
            Issue conflict,
            IssueDecision decision,
            Issue gap,
            IssueAnswer answer,
            UUID generationRequestId
    ) {
    }
}
