package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wevo.backend.ai.context.AiContextAssembler;
import com.wevo.backend.ai.context.AiInputSnapshot;
import com.wevo.backend.ai.context.AssembledAiContext;
import com.wevo.backend.ai.context.DraftReviewContext;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiSectionCheck;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.ai.repository.AiSectionCheckRepository;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.section.domain.AiCheckStatus;
import com.wevo.backend.section.domain.DriftStatus;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.service.AiRewriteDraftCreateResult;
import com.wevo.backend.section.service.AiRewriteDraftWriter;
import com.wevo.backend.section.service.DriftedSection;
import com.wevo.backend.section.service.SectionAiContextQueryService;
import com.wevo.backend.section.service.SectionPrecheckStateService;
import com.wevo.backend.section.service.SectionVersionedContent;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrecheckRewriteServiceTest {

    private static final long SECTION_ID = 2L;
    private static final long USER_ID = 3L;
    private static final long CHECK_ID = 4L;
    private static final long DRAFT_ID = 5L;
    private static final String HASH = "a".repeat(64);
    private static final UUID REQUEST_ID = UUID.randomUUID();

    @Mock private SectionAccessGuard sectionAccessGuard;
    @Mock private ProjectAccessGuard projectAccessGuard;
    @Mock private SectionPrecheckStateService precheckStateService;
    @Mock private SectionAiContextQueryService sectionQueryService;
    @Mock private AiContextAssembler contextAssembler;
    @Mock private AiJobRepository jobRepository;
    @Mock private AiSectionCheckRepository checkRepository;
    @Mock private AiRewriteDraftWriter draftWriter;
    @Mock private ProjectSection section;
    @Mock private Project project;
    @Mock private VerifiedProjectAccess access;
    @Mock private AiSectionCheck check;
    @Mock private AiJob sourceJob;
    @Mock private AiJob latestSuccessful;
    @Mock private DraftReviewContext context;

    private PrecheckRewriteService service;

    @BeforeEach
    void setUp() {
        service = new PrecheckRewriteService(
                sectionAccessGuard,
                projectAccessGuard,
                precheckStateService,
                sectionQueryService,
                contextAssembler,
                jobRepository,
                checkRepository,
                draftWriter
        );
        given(sectionAccessGuard.requireParticipantSection(SECTION_ID, USER_ID))
                .willReturn(section);
        given(section.getProject()).willReturn(project);
        given(project.getId()).willReturn(1L);
        given(projectAccessGuard.requireParticipantAccess(1L, USER_ID)).willReturn(access);
        given(checkRepository.findBySectionAndRequestIdForUpdate(SECTION_ID, REQUEST_ID))
                .willReturn(Optional.of(check));
        given(jobRepository
                .findTopByProjectSection_IdAndFeatureAndStatusOrderByCompletedAtDescIdDesc(
                        org.mockito.ArgumentMatchers.eq(SECTION_ID),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .willReturn(Optional.of(latestSuccessful));
        given(latestSuccessful.getResultId()).willReturn(CHECK_ID);
        given(latestSuccessful.getRequestId()).willReturn(REQUEST_ID);
        given(check.getId()).willReturn(CHECK_ID);
        given(check.getSourceJob()).willReturn(sourceJob);
        given(sourceJob.getRequestId()).willReturn(REQUEST_ID);
        given(sectionQueryService.getLatestDraft(access, SECTION_ID))
                .willReturn(new SectionVersionedContent(SECTION_ID, DRAFT_ID, 3, "현재 본문"));
        given(section.getStatus()).willReturn(ProjectSectionStatus.DRAFTING);
        given(section.getAiCheckStatus()).willReturn(AiCheckStatus.CURRENT);
        given(check.getCheckedContentVersion()).willReturn(3);
        given(check.getCheckedDraftId()).willReturn(DRAFT_ID);
        given(check.getInputSnapshotHash()).willReturn(HASH);
        given(check.getRewriteContent()).willReturn("개선 본문");
        given(context.sourceVersion()).willReturn("draft-v3-deps-0123456789abcdef");
        AssembledAiContext<DraftReviewContext> assembled = new AssembledAiContext<>(
                context, new AiInputSnapshot(new byte[]{1}, HASH));
        given(contextAssembler.assembleDraftReview(access, SECTION_ID))
                .willReturn(assembled);
    }

    @Test
    void appliesCurrentRewriteOnceAndRebindsCheck() {
        DriftedSection drifted = new DriftedSection(
                7L,
                "하위 섹션",
                ProjectSectionStatus.REVIEWING,
                DriftStatus.REVIEW_REQUIRED
        );
        given(draftWriter.append(org.mockito.ArgumentMatchers.any()))
                .willReturn(new AiRewriteDraftCreateResult(
                        6L, 4, ProjectSectionStatus.DRAFTING, java.util.List.of(drifted)));

        PrecheckRewriteApplyResult result =
                service.apply(SECTION_ID, USER_ID, REQUEST_ID, 3);

        assertThat(result.contentVersion()).isEqualTo(4);
        assertThat(result.driftedSections()).containsExactly(drifted);
        verify(check).bindAppliedRewrite(6L, 4);
        verify(draftWriter).append(org.mockito.ArgumentMatchers.argThat(command ->
                command.expectedBaseVersion() == 3
                        && command.content().equals("개선 본문")));
    }

    @Test
    void repeatedAppliedRequestReturnsBeforeLeaseWriter() {
        given(check.isRewriteApplied()).willReturn(true);
        given(check.getAppliedContentVersion()).willReturn(3);

        PrecheckRewriteApplyResult result =
                service.apply(SECTION_ID, USER_ID, REQUEST_ID, 1);

        assertThat(result.contentVersion()).isEqualTo(3);
        verify(draftWriter, never()).append(org.mockito.ArgumentMatchers.any());
        verify(contextAssembler, never()).assembleDraftReview(access, SECTION_ID);
    }

    @Test
    void rejectsOwnOrPrerequisiteSnapshotChangesWithoutDraftAppend() {
        AssembledAiContext<DraftReviewContext> changed = new AssembledAiContext<>(
                context, new AiInputSnapshot(new byte[]{2}, "b".repeat(64)));
        given(contextAssembler.assembleDraftReview(access, SECTION_ID))
                .willReturn(changed);

        assertConflict(() -> service.apply(SECTION_ID, USER_ID, REQUEST_ID, 3));
        verify(draftWriter, never()).append(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsAppliedResultAfterDraftChanged() {
        given(check.isRewriteApplied()).willReturn(true);
        given(check.getAppliedContentVersion()).willReturn(2);

        assertConflict(() -> service.apply(SECTION_ID, USER_ID, REQUEST_ID, 3));
        verify(draftWriter, never()).append(org.mockito.ArgumentMatchers.any());
    }

    private void assertConflict(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));
    }
}
