package com.wevo.backend.section;

import static org.assertj.core.api.Assertions.assertThat;

import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.service.DraftGenerationContract;
import com.wevo.backend.ai.service.SynthesisContract;
import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
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
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.review.domain.ReviewLink;
import com.wevo.backend.review.domain.ReviewLinkStatus;
import com.wevo.backend.review.domain.TeamReview;
import com.wevo.backend.review.domain.TeamReviewStatus;
import com.wevo.backend.review.repository.ReviewLinkRepository;
import com.wevo.backend.review.repository.TeamReviewRepository;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.domain.SectionDraft;
import com.wevo.backend.section.domain.SectionDraftEvidence;
import com.wevo.backend.section.domain.SectionDraftSource;
import com.wevo.backend.section.repository.SectionDraftEvidenceDecisionRepository;
import com.wevo.backend.section.repository.SectionDraftEvidenceGapAnswerRepository;
import com.wevo.backend.section.repository.SectionDraftEvidenceOpinionRepository;
import com.wevo.backend.section.repository.SectionDraftEvidenceRepository;
import com.wevo.backend.section.repository.SectionDraftRepository;
import com.wevo.backend.section.service.AiSectionDraftCreateCommand;
import com.wevo.backend.section.service.AiSectionDraftCreateCommand.DecisionEvidence;
import com.wevo.backend.section.service.AiSectionDraftCreateCommand.GapAnswerEvidence;
import com.wevo.backend.section.service.AiSectionDraftCreateCommand.OpinionEvidence;
import com.wevo.backend.section.service.AiSectionDraftWriter;
import com.wevo.backend.section.service.SectionDraftEvidenceQueryService;
import com.wevo.backend.section.service.SectionDraftEvidenceView;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Import(PostgresTestContainerConfig.class)
@Transactional
class AiSectionDraftWriterIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired private AiSectionDraftWriter writer;
    @Autowired private SectionDraftEvidenceQueryService evidenceQueryService;
    @Autowired private ProjectAccessGuard projectAccessGuard;
    @Autowired private SectionDraftRepository draftRepository;
    @Autowired private SectionDraftEvidenceRepository evidenceRepository;
    @Autowired private SectionDraftEvidenceOpinionRepository opinionEvidenceRepository;
    @Autowired private SectionDraftEvidenceDecisionRepository decisionEvidenceRepository;
    @Autowired private SectionDraftEvidenceGapAnswerRepository gapAnswerEvidenceRepository;
    @Autowired private TeamReviewRepository teamReviewRepository;
    @Autowired private ReviewLinkRepository reviewLinkRepository;
    @PersistenceContext private EntityManager em;

    @Test
    void firstGenerationCreatesVersionOneAndImmutableAggregateEvidence() {
        Fixture fixture = fixture(false);

        Long draftId = writer.createAiDraft(command(fixture, 0, "AI 첫 초안"));
        em.flush();
        em.clear();

        SectionDraft draft = draftRepository.findById(draftId).orElseThrow();
        assertThat(draft.getVersion()).isEqualTo(1);
        assertThat(draft.getSource()).isEqualTo(SectionDraftSource.AI_GENERATED);
        assertThat(draft.getProjectSection().getStatus()).isEqualTo(ProjectSectionStatus.DRAFTING);

        SectionDraftEvidence evidence =
                evidenceRepository.findByGenerationRequestId(fixture.generationRequestId())
                        .orElseThrow();
        assertThat(evidence.getSynthesisSetId()).isEqualTo(fixture.synthesisSet().getId());
        assertThat(evidence.getConsensusSummary()).isEqualTo("생성 시점 합의");
        assertThat(opinionEvidenceRepository
                .findAllByDraftEvidence_IdOrderBySortOrderAsc(evidence.getId()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getOpinionId()).isEqualTo(fixture.opinion().getId());
                    assertThat(item.getContent()).isEqualTo("제출 의견 스냅샷");
                });
        assertThat(decisionEvidenceRepository
                .findAllByDraftEvidence_IdOrderBySortOrderAsc(evidence.getId()))
                .singleElement()
                .satisfies(item -> assertThat(item.getDecision())
                        .isEqualTo("OWNER 확정 결정"));
        assertThat(gapAnswerEvidenceRepository
                .findAllByDraftEvidence_IdOrderBySortOrderAsc(evidence.getId()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getAnswerId()).isEqualTo(fixture.answer().getId());
                    assertThat(item.isInherited()).isTrue();
                });

        SectionDraftEvidenceView view = evidenceQueryService.getLatest(
                projectAccessGuard.requireParticipantAccess(
                        draft.getProjectSection().getProject().getId(),
                        fixture.owner().getId()),
                fixture.section().getId()
        );
        assertThat(view.draftId()).isEqualTo(draftId);
        assertThat(view.opinions()).singleElement()
                .satisfies(item -> assertThat(item.opinionId())
                        .isEqualTo(fixture.opinion().getId()));
    }

    @Test
    void regenerationAppendsVersionAndOutdatesExistingReviews() {
        Fixture fixture = fixture(true);

        Long draftId = writer.createAiDraft(command(fixture, 1, "AI 재생성 초안"));
        em.flush();
        em.clear();

        assertThat(draftRepository.findById(draftId).orElseThrow().getVersion()).isEqualTo(2);
        assertThat(draftRepository.findAll()).hasSize(2);
        assertThat(teamReviewRepository.findById(fixture.teamReviewId()).orElseThrow().isOutdated())
                .isTrue();
        assertThat(reviewLinkRepository.findById(fixture.reviewLinkId()).orElseThrow().getStatus())
                .isEqualTo(ReviewLinkStatus.OUTDATED);
    }

    private AiSectionDraftCreateCommand command(
            Fixture fixture,
            int baseVersion,
            String content
    ) {
        return new AiSectionDraftCreateCommand(
                fixture.section().getId(),
                fixture.owner().getId(),
                content,
                baseVersion,
                fixture.synthesisSet().getId(),
                0,
                fixture.generationRequestId(),
                "a".repeat(64),
                "draft-v1-s" + fixture.synthesisSet().getId() + "-g0",
                "생성 시점 합의",
                List.of(new OpinionEvidence(
                        fixture.opinion().getId(), fixture.owner().getName(), "제출 의견 스냅샷")),
                List.of(new DecisionEvidence(
                        fixture.conflict().getId(),
                        fixture.decision().getId(),
                        fixture.conflict().getQuestion(),
                        "OWNER 확정 결정")),
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

    private Fixture fixture(boolean withExistingDraftAndReviews) {
        User owner = User.builder()
                .name("팀장")
                .email(UUID.randomUUID() + "@wevo.com")
                .status(UserStatus.ACTIVE)
                .build();
        em.persist(owner);
        Project project = Project.builder()
                .owner(owner)
                .title("프로젝트")
                .resultType(OutputType.PROPOSAL)
                .audience("팀")
                .status(ProjectStatus.ACTIVE)
                .build();
        em.persist(project);
        em.persist(ProjectMember.builder()
                .project(project)
                .user(owner)
                .role(ProjectMemberRole.OWNER)
                .joinedAt(LocalDateTime.now(KST))
                .build());
        ProjectSection section = ProjectSection.builder()
                .project(project)
                .title("문제 정의")
                .sectionOrder(1)
                .status(ProjectSectionStatus.SYNTHESIZING)
                .build();
        em.persist(section);
        em.flush();

        UUID synthesisRequestId = UUID.randomUUID();
        em.persist(job(synthesisRequestId, project, section, owner,
                AiFeature.OPINION_SYNTHESIS, "b".repeat(64)));
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
                AiFeature.DRAFT_GENERATION, "c".repeat(64)));

        Long teamReviewId = null;
        Long reviewLinkId = null;
        if (withExistingDraftAndReviews) {
            SectionDraft existing = SectionDraft.builder()
                    .projectSection(section)
                    .content("기존 v1")
                    .version(1)
                    .lastEditor(owner)
                    .build();
            em.persist(existing);
            TeamReview review = TeamReview.builder()
                    .projectSection(section)
                    .reviewer(owner)
                    .status(TeamReviewStatus.APPROVED)
                    .reviewedContentVersion(1)
                    .build();
            em.persist(review);
            ReviewLink link = ReviewLink.builder()
                    .projectSection(section)
                    .createdBy(owner)
                    .tokenHash(UUID.randomUUID().toString().replace("-", ""))
                    .sectionTitleSnapshot(section.getTitle())
                    .contentSnapshot("기존 v1")
                    .contentVersion(1)
                    .status(ReviewLinkStatus.ACTIVE)
                    .build();
            em.persist(link);
            em.flush();
            teamReviewId = review.getId();
            reviewLinkId = link.getId();
        }
        em.flush();
        return new Fixture(
                owner, section, set, opinion, conflict, decision, gap, answer,
                generationRequestId, teamReviewId, reviewLinkId);
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
            UUID generationRequestId,
            Long teamReviewId,
            Long reviewLinkId
    ) {
    }
}
