package com.wevo.backend.ai.context;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.issue.service.CurrentSynthesisContext;
import com.wevo.backend.issue.service.ConflictDecisionContext;
import com.wevo.backend.issue.service.GapAnswerContext;
import com.wevo.backend.issue.service.GapIssueContext;
import com.wevo.backend.issue.service.SynthesisSetQueryService;
import com.wevo.backend.issue.service.SynthesisOpinionEvidenceContext;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.opinion.service.SubmittedOpinionContext;
import com.wevo.backend.opinion.service.SubmittedOpinionQueryService;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.service.ProjectAiContext;
import com.wevo.backend.project.service.ProjectAiContextQueryService;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.project.service.VerifiedSectionAccess;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.service.PrerequisiteSectionContent;
import com.wevo.backend.section.service.SectionAiContextQueryService;
import com.wevo.backend.section.service.SectionAiMetadata;
import com.wevo.backend.section.service.SectionVersionedContent;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiContextAssemblerTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long SECTION_ID = 10L;

    private ProjectAiContextQueryService projectQueryService;
    private SubmittedOpinionQueryService opinionQueryService;
    private SectionAiContextQueryService sectionQueryService;
    private SynthesisSetQueryService synthesisQueryService;
    private SectionAccessGuard sectionAccessGuard;
    private VerifiedProjectAccess access;
    private AiContextAssembler assembler;

    @BeforeEach
    void setUp() {
        projectQueryService = mock(ProjectAiContextQueryService.class);
        opinionQueryService = mock(SubmittedOpinionQueryService.class);
        sectionQueryService = mock(SectionAiContextQueryService.class);
        synthesisQueryService = mock(SynthesisSetQueryService.class);
        sectionAccessGuard = mock(SectionAccessGuard.class);
        access = mock(VerifiedProjectAccess.class);
        VerifiedSectionAccess sectionAccess = mock(VerifiedSectionAccess.class);

        when(access.projectId()).thenReturn(PROJECT_ID);
        when(sectionAccessGuard.verifySectionAccess(access, SECTION_ID)).thenReturn(sectionAccess);
        when(projectQueryService.getProjectContext(access)).thenReturn(new ProjectAiContext(
                PROJECT_ID,
                "프로젝트",
                "설명\r\n한 줄",
                "아이디어",
                "팀 리더",
                OutputType.PRESENTATION
        ));
        when(sectionQueryService.getMetadata(access, SECTION_ID)).thenReturn(metadata(3L, false));
        when(sectionQueryService.findDirectConfirmedPrerequisites(access, SECTION_ID))
                .thenReturn(List.of(prerequisite(20L, 1, 2)));
        when(sectionQueryService.findDirectLatestPrerequisites(access, SECTION_ID))
                .thenReturn(List.of(prerequisite(20L, 1, 3)));
        when(sectionQueryService.getLatestDraft(access, SECTION_ID))
                .thenReturn(new SectionVersionedContent(SECTION_ID, 4, "현재\r\n초안"));
        when(sectionQueryService.getLatestDraftOrEmpty(access, SECTION_ID))
                .thenReturn(new SectionVersionedContent(SECTION_ID, 0, null));
        when(synthesisQueryService.getCurrentForAiContext(sectionAccess))
                .thenReturn(synthesis(100L, 3L, "GAP 답변"));
        when(synthesisQueryService.getCurrentForDraftGeneration(sectionAccess))
                .thenReturn(synthesis(100L, 3L, "GAP 답변"));
        when(opinionQueryService.findSubmittedOpinions(access, SECTION_ID))
                .thenReturn(opinionsInRepositoryOrder());

        AiProperties properties = properties(20_000);
        assembler = new AiContextAssembler(
                projectQueryService,
                opinionQueryService,
                sectionQueryService,
                synthesisQueryService,
                sectionAccessGuard,
                properties,
                new AiInputSnapshotHasher()
        );
    }

    @Test
    void canonicalizesRepositoryOrderAndAssignsContextLocalAliasesDeterministically() {
        AssembledAiContext<IssueDetectionContext> first =
                assembler.assembleIssueDetection(access, SECTION_ID);

        when(opinionQueryService.findSubmittedOpinions(access, SECTION_ID))
                .thenReturn(List.of(
                        opinionsInRepositoryOrder().get(1),
                        opinionsInRepositoryOrder().get(2),
                        opinionsInRepositoryOrder().get(0)
                ));
        AssembledAiContext<IssueDetectionContext> second =
                assembler.assembleIssueDetection(access, SECTION_ID);

        assertThat(first.context().opinions())
                .extracting(AiOpinionContext::opinionId)
                .containsExactly(11L, 12L, 13L);
        assertThat(first.context().opinions())
                .extracting(AiOpinionContext::authorAlias)
                .containsExactly("member-1", "member-2", "member-1");
        assertThat(second.snapshot().canonicalBytes())
                .containsExactly(first.snapshot().canonicalBytes());
        assertThat(second.snapshot().inputSnapshotHash())
                .isEqualTo(first.snapshot().inputSnapshotHash());

        String canonical = new String(first.snapshot().canonicalBytes(), StandardCharsets.UTF_8);
        assertThat(canonical)
                .contains("member-1", "member-2", "설명\\n한 줄")
                .doesNotContain("9001", "9002", "\r");
    }

    @Test
    void synthesisSnapshotChangesForSubmittedContentGapAnswerAndGateGeneration() {
        String baseline = assembler.assembleOpinionSynthesis(access, SECTION_ID)
                .snapshot().inputSnapshotHash();

        List<SubmittedOpinionContext> changedOpinions = List.of(
                opinion(11L, "재제출 내용", 1, 9001L),
                opinion(12L, "두 번째", 2, 9002L),
                opinion(13L, "세 번째", 2, 9001L)
        );
        when(opinionQueryService.findSubmittedOpinions(access, SECTION_ID)).thenReturn(changedOpinions);
        String resubmitted = assembler.assembleOpinionSynthesis(access, SECTION_ID)
                .snapshot().inputSnapshotHash();

        when(opinionQueryService.findSubmittedOpinions(access, SECTION_ID))
                .thenReturn(opinionsInRepositoryOrder());
        VerifiedSectionAccess sectionAccess = sectionAccessGuard.verifySectionAccess(access, SECTION_ID);
        when(synthesisQueryService.getCurrentForAiContext(sectionAccess))
                .thenReturn(synthesis(100L, 3L, "바뀐 GAP 답변"));
        String gapChanged = assembler.assembleOpinionSynthesis(access, SECTION_ID)
                .snapshot().inputSnapshotHash();

        when(synthesisQueryService.getCurrentForAiContext(sectionAccess))
                .thenReturn(synthesis(100L, 3L, "GAP 답변"));
        when(sectionQueryService.getMetadata(access, SECTION_ID)).thenReturn(metadata(4L, false));
        String gateChanged = assembler.assembleOpinionSynthesis(access, SECTION_ID)
                .snapshot().inputSnapshotHash();

        assertThat(List.of(baseline, resubmitted, gapChanged, gateChanged))
                .doesNotHaveDuplicates();
    }

    @Test
    void reviewSnapshotIncludesOwnAndEveryDirectLatestVersionButExcludesProjectBrief() {
        AssembledAiContext<DraftReviewContext> baseline =
                assembler.assembleDraftReview(access, SECTION_ID);
        String canonical = new String(baseline.snapshot().canonicalBytes(), StandardCharsets.UTF_8);

        when(sectionQueryService.getLatestDraft(access, SECTION_ID))
                .thenReturn(new SectionVersionedContent(SECTION_ID, 5, "현재 초안"));
        String ownVersionChanged = assembler.assembleDraftReview(access, SECTION_ID)
                .snapshot().inputSnapshotHash();

        when(sectionQueryService.getLatestDraft(access, SECTION_ID))
                .thenReturn(new SectionVersionedContent(SECTION_ID, 4, "현재\r\n초안"));
        when(sectionQueryService.findDirectLatestPrerequisites(access, SECTION_ID))
                .thenReturn(List.of(prerequisite(20L, 1, 4)));
        String prerequisiteChanged = assembler.assembleDraftReview(access, SECTION_ID)
                .snapshot().inputSnapshotHash();

        assertThat(ownVersionChanged).isNotEqualTo(baseline.snapshot().inputSnapshotHash());
        assertThat(prerequisiteChanged).isNotEqualTo(baseline.snapshot().inputSnapshotHash());
        assertThat(canonical).doesNotContain("아이디어", "팀 리더", "\"projectBrief\"");
        assertThat(baseline.context().sourceVersion()).hasSizeLessThanOrEqualTo(100);
    }

    @Test
    void draftGenerationRejectsStaleOrWrongGenerationSynthesisWithoutEmptyFallback() {
        when(sectionQueryService.getMetadata(access, SECTION_ID)).thenReturn(metadata(3L, true));

        assertThatThrownBy(() -> assembler.assembleDraftGeneration(access, SECTION_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void draftSnapshotChangesWithConflictDecisionAndBaseDraftVersion() {
        VerifiedSectionAccess sectionAccess =
                sectionAccessGuard.verifySectionAccess(access, SECTION_ID);
        when(synthesisQueryService.getCurrentForDraftGeneration(sectionAccess))
                .thenReturn(draftSynthesis("OWNER 결정 A"));
        String baseline = assembler.assembleDraftGeneration(access, SECTION_ID)
                .snapshot().inputSnapshotHash();

        when(synthesisQueryService.getCurrentForDraftGeneration(sectionAccess))
                .thenReturn(draftSynthesis("OWNER 결정 B"));
        String decisionChanged = assembler.assembleDraftGeneration(access, SECTION_ID)
                .snapshot().inputSnapshotHash();

        when(synthesisQueryService.getCurrentForDraftGeneration(sectionAccess))
                .thenReturn(draftSynthesis("OWNER 결정 A"));
        when(sectionQueryService.getLatestDraftOrEmpty(access, SECTION_ID))
                .thenReturn(new SectionVersionedContent(SECTION_ID, 2, "기존 팀 편집본"));
        String baseDraftChanged = assembler.assembleDraftGeneration(access, SECTION_ID)
                .snapshot().inputSnapshotHash();

        assertThat(decisionChanged).isNotEqualTo(baseline);
        assertThat(baseDraftChanged).isNotEqualTo(baseline);
    }

    private List<SubmittedOpinionContext> opinionsInRepositoryOrder() {
        return List.of(
                opinion(13L, "세 번째", 2, 9001L),
                opinion(11L, "첫 번째", 1, 9001L),
                opinion(12L, "두 번째", 2, 9002L)
        );
    }

    private SubmittedOpinionContext opinion(
            long opinionId,
            String content,
            int minute,
            long authorReference
    ) {
        return new SubmittedOpinionContext(
                opinionId,
                content,
                LocalDateTime.of(2026, 7, 24, 10, minute),
                authorReference
        );
    }

    private SectionAiMetadata metadata(long generation, boolean stale) {
        return new SectionAiMetadata(
                SECTION_ID,
                PROJECT_ID,
                "문제 정의",
                2,
                ProjectSectionStatus.SYNTHESIZING,
                generation,
                stale,
                "problem-definition",
                "문제를 설명합니다.",
                "근거를 포함하세요."
        );
    }

    private CurrentSynthesisContext synthesis(
            long synthesisSetId,
            long generation,
            String answerContent
    ) {
        return new CurrentSynthesisContext(
                synthesisSetId,
                generation,
                "현재 합의",
                List.of(new GapAnswerContext(
                        501L,
                        601L,
                        answerContent,
                        LocalDateTime.of(2026, 7, 24, 11, 0)
                ))
        );
    }

    private CurrentSynthesisContext draftSynthesis(String decision) {
        return new CurrentSynthesisContext(
                100L,
                3L,
                "현재 합의",
                List.of(new GapAnswerContext(
                        501L,
                        601L,
                        "GAP 답변",
                        LocalDateTime.of(2026, 7, 24, 11, 0),
                        "팀원",
                        false
                )),
                List.of(new SynthesisOpinionEvidenceContext(11L, "팀원", "근거 의견")),
                List.of(new ConflictDecisionContext(
                        701L,
                        801L,
                        "충돌",
                        "무엇을 선택할까요?",
                        decision,
                        List.of(11L)
                )),
                List.of(new GapIssueContext(501L, "보충 근거", true, List.of(11L))),
                false
        );
    }

    private PrerequisiteSectionContent prerequisite(
            long sectionId,
            int sectionOrder,
            int contentVersion
    ) {
        return new PrerequisiteSectionContent(
                sectionId,
                "parent-" + sectionId,
                sectionOrder,
                contentVersion,
                "상위 본문 " + contentVersion
        );
    }

    private AiProperties properties(int maxInputTokens) {
        return new AiProperties(
                "none",
                new AiProperties.ModelOptions(
                        "test-model",
                        Duration.ofSeconds(1),
                        maxInputTokens,
                        100,
                        30_000,
                        1_000,
                        AiProperties.ModelOptions.CONSERVATIVE_CHAR_V1,
                        AiProperties.ModelOptions.REJECT_OVERSIZED_INPUT_V1,
                        0,
                        Duration.ZERO,
                        Duration.ZERO
                ),
                Map.of(),
                null
        );
    }
}
