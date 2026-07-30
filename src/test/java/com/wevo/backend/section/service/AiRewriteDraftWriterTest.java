package com.wevo.backend.section.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.review.service.ReviewLinkService;
import com.wevo.backend.review.service.TeamReviewService;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.domain.SectionDraft;
import com.wevo.backend.section.domain.SectionDraftSource;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import com.wevo.backend.section.repository.SectionDraftRepository;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.service.UserService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiRewriteDraftWriterTest {

    @Mock private ProjectSectionRepository projectSectionRepository;
    @Mock private SectionDraftRepository sectionDraftRepository;
    @Mock private DraftLeaseService draftLeaseService;
    @Mock private ReviewLinkService reviewLinkService;
    @Mock private TeamReviewService teamReviewService;
    @Mock private UserService userService;
    @Mock private SectionDriftService sectionDriftService;
    @Mock private ProjectSection section;
    @Mock private User user;

    private AiRewriteDraftWriter writer;
    private SectionDraft latest;

    @BeforeEach
    void setUp() {
        writer = new AiRewriteDraftWriter(
                projectSectionRepository,
                sectionDraftRepository,
                draftLeaseService,
                reviewLinkService,
                teamReviewService,
                userService,
                sectionDriftService
        );
        latest = SectionDraft.builder()
                .projectSection(section)
                .content("현재 본문")
                .version(3)
                .lastEditor(user)
                .source(SectionDraftSource.USER_EDITED)
                .build();
        ReflectionTestUtils.setField(latest, "id", 30L);
        given(projectSectionRepository.findByIdForUpdate(2L))
                .willReturn(Optional.of(section));
        given(section.getStatus()).willReturn(ProjectSectionStatus.DRAFTING);
        given(sectionDraftRepository.findTopByProjectSection_IdOrderByVersionDesc(2L))
                .willReturn(Optional.of(latest));
        given(userService.getUserReference(3L)).willReturn(user);
        given(sectionDraftRepository.saveAndFlush(any())).willAnswer(invocation -> {
            SectionDraft draft = invocation.getArgument(0);
            ReflectionTestUtils.setField(draft, "id", 31L);
            return draft;
        });
    }

    @Test
    void appendsAiRewriteAndInvalidatesOtherReviewsThenReleasesLease() {
        AiRewriteDraftCreateResult result = writer.append(
                new AiRewriteDraftCreateCommand(2L, 3L, 3, "개선 본문"));

        assertThat(result.draftId()).isEqualTo(31L);
        assertThat(result.contentVersion()).isEqualTo(4);
        ArgumentCaptor<SectionDraft> captor = ArgumentCaptor.forClass(SectionDraft.class);
        verify(sectionDraftRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo(SectionDraftSource.AI_REWRITE_APPLIED);
        assertThat(captor.getValue().getContent()).isEqualTo("개선 본문");
        verify(section).bindCurrentAiCheck();
        verify(reviewLinkService).markSectionLinksOutdated(2L);
        verify(teamReviewService).markSectionTeamReviewsOutdated(2L);
        verify(sectionDriftService).propagateConfirmedContentChange(section, 3L, 4);

        InOrder leaseOrder = inOrder(draftLeaseService, sectionDraftRepository);
        leaseOrder.verify(draftLeaseService).requireActiveHolder(2L, 3L);
        leaseOrder.verify(sectionDraftRepository).saveAndFlush(any());
        leaseOrder.verify(draftLeaseService).releaseHeldBy(2L, 3L);
    }

    @Test
    void leaseFailurePreservesDraftAndReviews() {
        org.mockito.Mockito.doThrow(
                        new BusinessException(ErrorCode.DRAFT_LEASE_NOT_HELD))
                .when(draftLeaseService).requireActiveHolder(2L, 3L);

        assertThatThrownBy(() -> writer.append(
                new AiRewriteDraftCreateCommand(2L, 3L, 3, "개선 본문")))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.DRAFT_LEASE_NOT_HELD));
        verify(sectionDraftRepository, never()).saveAndFlush(any());
        verify(reviewLinkService, never()).markSectionLinksOutdated(any());
        verify(teamReviewService, never()).markSectionTeamReviewsOutdated(any());
    }

    @Test
    void baseVersionMismatchIsConflictBeforeLeaseCheck() {
        assertThatThrownBy(() -> writer.append(
                new AiRewriteDraftCreateCommand(2L, 3L, 2, "개선 본문")))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));
        verify(draftLeaseService, never()).requireActiveHolder(any(), any());
    }
}
