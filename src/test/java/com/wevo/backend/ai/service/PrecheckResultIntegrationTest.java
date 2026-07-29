package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wevo.backend.ai.context.AiProjectIdentity;
import com.wevo.backend.ai.context.AiContextAssembler;
import com.wevo.backend.ai.context.AssembledAiContext;
import com.wevo.backend.ai.context.AiSectionContext;
import com.wevo.backend.ai.context.AiTemplateContext;
import com.wevo.backend.ai.context.DraftReviewContext;
import com.wevo.backend.ai.domain.AiErrorType;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiRequestStatus;
import com.wevo.backend.ai.domain.AiSectionCheckFinding;
import com.wevo.backend.ai.dto.model.DraftReviewOutput;
import com.wevo.backend.ai.dto.model.DraftReviewFindingOutput;
import com.wevo.backend.ai.dto.model.DraftReviewRewriteOutput;
import com.wevo.backend.ai.domain.AiSectionFindingType;
import com.wevo.backend.ai.dto.response.PrecheckResponse;
import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.section.domain.AiCheckStatus;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.domain.SectionDraft;
import com.wevo.backend.section.domain.SectionDraftSource;
import com.wevo.backend.section.repository.DraftLeaseRepository;
import com.wevo.backend.section.repository.SectionDraftRepository;
import com.wevo.backend.section.repository.SectionTemplateRepository;
import com.wevo.backend.section.service.DraftLeaseService;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "wevo.ai.jobs.dispatch-enabled=false"
})
@Import(PostgresTestContainerConfig.class)
@Transactional
class PrecheckResultIntegrationTest {

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 7, 29, 10, 0);

    @Autowired private PrecheckResultWriter resultWriter;
    @Autowired private PrecheckQueryService queryService;
    @Autowired private PrecheckRewriteService rewriteService;
    @Autowired private AiContextAssembler contextAssembler;
    @Autowired private ProjectAccessGuard projectAccessGuard;
    @Autowired private DraftLeaseService draftLeaseService;
    @Autowired private SectionDraftRepository draftRepository;
    @Autowired private DraftLeaseRepository draftLeaseRepository;
    @Autowired private SectionTemplateRepository templateRepository;
    @Autowired private com.wevo.backend.ai.repository.AiSectionCheckRepository checkRepository;
    @PersistenceContext private EntityManager em;

    @Test
    void successBindsExactDraftAndFailureRerunPreservesCurrentResult() {
        Fixture fixture = fixture();
        DraftReviewContext context = context(fixture);
        Long resultId = resultWriter.persist(
                fixture.successJob().getRequestId(),
                context,
                new DraftReviewOutput(
                        List.of(),
                        new DraftReviewRewriteOutput(fixture.draft().getContent(), 0)
                )
        );
        fixture.successJob().succeed(resultId, NOW.plusSeconds(2));

        AiJob failedRerun = job(
                fixture.project(),
                fixture.section(),
                fixture.owner(),
                "b".repeat(64),
                randomHash());
        em.persist(failedRerun);
        failedRerun.start(NOW.plusSeconds(3));
        failedRerun.fail(
                AiErrorType.PROVIDER_TIMEOUT,
                "AI 응답 시간이 초과되었습니다.",
                NOW.plusSeconds(4));
        em.flush();
        em.clear();

        PrecheckResponse response =
                queryService.getPrecheck(fixture.section().getId(), fixture.owner().getId());

        assertThat(response.exists()).isTrue();
        assertThat(response.aiCheckStatus()).isEqualTo(AiCheckStatus.CURRENT);
        assertThat(response.latestJob().requestId()).isEqualTo(failedRerun.getRequestId());
        assertThat(response.latestJob().status()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(response.latestJob().failure().errorCode()).isEqualTo("AI005");
        assertThat(response.currentResult().resultId())
                .isEqualTo(fixture.successJob().getRequestId());
        assertThat(response.currentResult().checkedContentVersion()).isEqualTo(1);
        assertThat(response.currentResult().findings()).isEmpty();
        assertThat(response.currentResult().rewrite().content())
                .isEqualTo("검토할 현재 본문");
        assertThat(response.currentResult().rewriteApplied()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"target_excerpt", "comment_text", "suggestion"})
    void databaseRejectsOverlongFindingText(String column) {
        int maxLength = switch (column) {
            case "target_excerpt" -> AiSectionCheckFinding.MAX_TARGET_EXCERPT_LENGTH;
            case "comment_text" -> AiSectionCheckFinding.MAX_COMMENT_LENGTH;
            case "suggestion" -> AiSectionCheckFinding.MAX_SUGGESTION_LENGTH;
            default -> throw new IllegalArgumentException("지원하지 않는 finding 컬럼입니다.");
        };
        Fixture fixture = fixture();
        Long resultId = resultWriter.persist(
                fixture.successJob().getRequestId(),
                context(fixture),
                new DraftReviewOutput(
                        List.of(new DraftReviewFindingOutput(
                                AiSectionFindingType.UNCLEAR_SENTENCE,
                                fixture.draft().getContent(),
                                "점검 의견",
                                "구체적으로 수정하세요.")),
                        new DraftReviewRewriteOutput("더 명확한 개선 본문", 1)
                )
        );
        em.flush();

        String sql = """
                UPDATE ai_section_check_findings
                SET %s = repeat('가', :length)
                WHERE ai_section_check_id = :resultId
                """.formatted(column);
        assertThatThrownBy(() -> em.createNativeQuery(sql)
                .setParameter("length", maxLength + 1)
                .setParameter("resultId", resultId)
                .executeUpdate())
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void rewriteApplyCreatesOneVersionRebindsCurrentAndIsIdempotentAfterLeaseRelease() {
        Fixture fixture = fixture();
        VerifiedProjectAccess access = projectAccessGuard.requireParticipantAccess(
                fixture.project().getId(), fixture.owner().getId());
        AssembledAiContext<DraftReviewContext> assembled =
                contextAssembler.assembleDraftReview(access, fixture.section().getId());
        AiJob job = AiJob.queue(
                UUID.randomUUID(),
                fixture.project(),
                fixture.section(),
                fixture.owner(),
                AiFeature.DRAFT_REVIEW,
                assembled.snapshot().inputSnapshotHash(),
                assembled.context().sourceVersion(),
                DraftReviewContract.PROMPT_VERSION,
                DraftReviewContract.SCHEMA_VERSION,
                "model",
                1_000,
                randomHash(),
                NOW.plusMinutes(1)
        );
        em.persist(job);
        job.start(NOW.plusMinutes(1).plusSeconds(1));
        em.flush();
        Long checkId = resultWriter.persist(
                job.getRequestId(),
                assembled.context(),
                new DraftReviewOutput(
                        List.of(new DraftReviewFindingOutput(
                                AiSectionFindingType.UNCLEAR_SENTENCE,
                                "검토할 현재 본문",
                                "표현을 더 명확히 할 수 있습니다.",
                                "핵심 대상을 명시하세요.")),
                        new DraftReviewRewriteOutput("더 명확한 개선 본문", 1)
                )
        );
        job.succeed(checkId, NOW.plusMinutes(1).plusSeconds(2));
        em.flush();

        draftLeaseService.acquire(fixture.section().getId(), fixture.owner().getId());
        PrecheckRewriteApplyResult first = rewriteService.apply(
                fixture.section().getId(),
                fixture.owner().getId(),
                job.getRequestId(),
                1
        );
        PrecheckRewriteApplyResult repeated = rewriteService.apply(
                fixture.section().getId(),
                fixture.owner().getId(),
                job.getRequestId(),
                1
        );
        em.flush();
        em.clear();

        assertThat(first.contentVersion()).isEqualTo(2);
        assertThat(repeated.contentVersion()).isEqualTo(2);
        assertThat(draftRepository
                .findTopByProjectSection_IdOrderByVersionDesc(fixture.section().getId())
                .orElseThrow())
                .satisfies(draft -> {
                    assertThat(draft.getVersion()).isEqualTo(2);
                    assertThat(draft.getContent()).isEqualTo("더 명확한 개선 본문");
                    assertThat(draft.getSource()).isEqualTo(SectionDraftSource.AI_REWRITE_APPLIED);
                });
        assertThat(checkRepository.findById(checkId).orElseThrow())
                .satisfies(check -> {
                    assertThat(check.isRewriteApplied()).isTrue();
                    assertThat(check.getCheckedContentVersion()).isEqualTo(2);
                    assertThat(check.getAppliedContentVersion()).isEqualTo(2);
                });
        assertThat(draftLeaseRepository.findByProjectSection_Id(fixture.section().getId())
                .orElseThrow().isActiveAt(LocalDateTime.now(ZoneId.of("Asia/Seoul"))))
                .isFalse();
    }

    private Fixture fixture() {
        User owner = User.builder()
                .name("팀장")
                .email(UUID.randomUUID() + "@wevo.com")
                .status(UserStatus.ACTIVE)
                .build();
        em.persist(owner);
        Project project = Project.builder()
                .owner(owner)
                .title("사전 검토 프로젝트")
                .resultType(OutputType.PROPOSAL)
                .audience("심사위원")
                .status(ProjectStatus.ACTIVE)
                .build();
        em.persist(project);
        em.persist(ProjectMember.builder()
                .project(project)
                .user(owner)
                .role(ProjectMemberRole.OWNER)
                .joinedAt(NOW)
                .build());
        ProjectSection section = ProjectSection.builder()
                .project(project)
                .template(templateRepository
                        .findByResultTypeOrderByOrderNo(OutputType.PROPOSAL)
                        .getFirst())
                .title("문제 정의")
                .sectionOrder(1)
                .status(ProjectSectionStatus.DRAFTING)
                .build();
        em.persist(section);
        SectionDraft draft = SectionDraft.builder()
                .projectSection(section)
                .content("검토할 현재 본문")
                .version(1)
                .lastEditor(owner)
                .source(SectionDraftSource.USER_EDITED)
                .build();
        em.persist(draft);
        AiJob successJob = job(
                project, section, owner, "a".repeat(64), randomHash());
        em.persist(successJob);
        successJob.start(NOW.plusSeconds(1));
        em.flush();
        return new Fixture(owner, project, section, draft, successJob);
    }

    private AiJob job(
            Project project,
            ProjectSection section,
            User owner,
            String inputHash,
            String idempotencyKey
    ) {
        return AiJob.queue(
                UUID.randomUUID(),
                project,
                section,
                owner,
                AiFeature.DRAFT_REVIEW,
                inputHash,
                "draft-v1-deps-e3b0c44298fc1c14",
                DraftReviewContract.PROMPT_VERSION,
                DraftReviewContract.SCHEMA_VERSION,
                "model",
                1_000,
                idempotencyKey,
                NOW
        );
    }

    private DraftReviewContext context(Fixture fixture) {
        return new DraftReviewContext(
                new AiProjectIdentity(
                        fixture.project().getId(),
                        fixture.project().getTitle(),
                        fixture.project().getResultType()),
                new AiSectionContext(
                        fixture.section().getId(),
                        fixture.section().getTitle(),
                        fixture.section().getSectionOrder(),
                        fixture.section().getStatus(),
                        0,
                        false,
                        new AiTemplateContext("problem", "문제 정의", "가이드")),
                fixture.draft().getId(),
                fixture.draft().getVersion(),
                fixture.draft().getContent(),
                List.of(),
                "e3b0c44298fc1c14"
        );
    }

    private String randomHash() {
        return (UUID.randomUUID().toString() + UUID.randomUUID())
                .replace("-", "");
    }

    private record Fixture(
            User owner,
            Project project,
            ProjectSection section,
            SectionDraft draft,
            AiJob successJob
    ) {
    }
}
