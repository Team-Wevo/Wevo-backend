package com.wevo.backend.opinion.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.opinion.domain.Opinion;
import com.wevo.backend.opinion.domain.OpinionStatus;
import com.wevo.backend.opinion.dto.request.OpinionDraftRequest;
import com.wevo.backend.opinion.dto.response.MyOpinionResponse;
import com.wevo.backend.opinion.dto.response.OpinionDraftResponse;
import com.wevo.backend.opinion.dto.response.OpinionGateCloseResponse;
import com.wevo.backend.opinion.dto.response.OpinionGateReopenResponse;
import com.wevo.backend.opinion.dto.response.OpinionSubmitResponse;
import com.wevo.backend.opinion.dto.response.SubmittedOpinionListResponse;
import com.wevo.backend.opinion.repository.OpinionRepository;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.service.DraftLeaseService;
import com.wevo.backend.section.service.SectionStatusService;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import com.wevo.backend.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OpinionServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long PROJECT_ID = 100L;
    private static final Long SECTION_ID = 10L;
    private static final String CONTENT = "타겟을 공모전 참가 대학생 팀으로 좁히는 게 좋겠습니다.";
    private static final String REVISED_CONTENT = "타겟을 대학생 팀에서 사회인 동아리까지 넓히면 좋겠습니다.";

    @Mock
    private SectionAccessGuard sectionAccessGuard;
    @Mock
    private OpinionRepository opinionRepository;
    @Mock
    private DraftLeaseService draftLeaseService;
    @Mock
    private SectionStatusService sectionStatusService;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OpinionService opinionService;

    @Test
    @DisplayName("의견이 없으면 DRAFT 상태로 새로 생성한다")
    void saveDraft_createsNewDraft_whenAbsent() {
        ProjectSection section = section(ProjectSectionStatus.COLLECTING);
        User author = user();
        given(sectionAccessGuard.requireParticipantSectionForUpdate(SECTION_ID, USER_ID))
                .willReturn(section);
        given(opinionRepository.findByProjectSection_IdAndAuthor_Id(SECTION_ID, USER_ID))
                .willReturn(Optional.empty());
        given(userRepository.getReferenceById(USER_ID)).willReturn(author);
        given(opinionRepository.save(any(Opinion.class))).willAnswer(invocation -> {
            Opinion opinion = invocation.getArgument(0);
            ReflectionTestUtils.setField(opinion, "id", 501L);
            return opinion;
        });

        OpinionDraftResponse response = opinionService.saveDraft(
                SECTION_ID, USER_ID, new OpinionDraftRequest(CONTENT));

        assertThat(response.id()).isEqualTo(501L);
        assertThat(response.content()).isEqualTo(CONTENT);

        ArgumentCaptor<Opinion> captor = ArgumentCaptor.forClass(Opinion.class);
        verify(opinionRepository).save(captor.capture());
        assertThat(captor.getValue().getProjectSection()).isEqualTo(section);
        assertThat(captor.getValue().getAuthor()).isEqualTo(author);
        assertThat(captor.getValue().getStatus()).isEqualTo(OpinionStatus.DRAFT);
    }

    @Test
    @DisplayName("임시저장은 작업본만 갱신하고 제출본은 유지한다 (재제출 모델 §4.1)")
    void saveDraft_updatesWorkingCopy_keepsSubmittedContent() {
        ProjectSection section = section(ProjectSectionStatus.COLLECTING);
        Opinion existing = submittedOpinion(501L, section, user(),
                LocalDateTime.of(2026, 7, 14, 12, 5));
        given(sectionAccessGuard.requireParticipantSectionForUpdate(SECTION_ID, USER_ID))
                .willReturn(section);
        given(opinionRepository.findByProjectSection_IdAndAuthor_Id(SECTION_ID, USER_ID))
                .willReturn(Optional.of(existing));

        OpinionDraftResponse response = opinionService.saveDraft(
                SECTION_ID, USER_ID, new OpinionDraftRequest(REVISED_CONTENT));

        assertThat(response.id()).isEqualTo(501L);
        assertThat(response.content()).isEqualTo(REVISED_CONTENT);
        // 팀에 공개되는 제출본은 그대로 — 재제출해야 갱신된다
        assertThat(existing.getSubmittedContentOrLegacy()).isEqualTo(CONTENT);
        assertThat(existing.getStatus()).isEqualTo(OpinionStatus.SUBMITTED);
        assertThat(existing.hasUnsubmittedChanges()).isTrue();
        verify(opinionRepository, never()).save(any(Opinion.class));
    }

    @Test
    @DisplayName("섹션이 없거나 비멤버면 SECTION_NOT_FOUND 예외를 그대로 전파한다 (존재 숨김)")
    void saveDraft_sectionHiddenOrMissing_throws() {
        given(sectionAccessGuard.requireParticipantSectionForUpdate(SECTION_ID, USER_ID))
                .willThrow(new BusinessException(ErrorCode.SECTION_NOT_FOUND));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> opinionService.saveDraft(SECTION_ID, USER_ID, new OpinionDraftRequest(CONTENT)));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SECTION_NOT_FOUND);
    }

    @Test
    @DisplayName("섹션이 의견 수집(COLLECTING) 단계가 아니면 OPINION_COLLECTION_CLOSED 예외를 던진다")
    void saveDraft_collectionClosed_throws() {
        ProjectSection section = section(ProjectSectionStatus.SYNTHESIZING);
        given(sectionAccessGuard.requireParticipantSectionForUpdate(SECTION_ID, USER_ID))
                .willReturn(section);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> opinionService.saveDraft(SECTION_ID, USER_ID, new OpinionDraftRequest(CONTENT)));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.OPINION_COLLECTION_CLOSED);
    }

    @Test
    @DisplayName("내가 작성한 의견이 있으면 exists=true 와 작업본 내용을 반환한다")
    void getMyOpinion_returnsOpinion_whenPresent() {
        ProjectSection section = section(ProjectSectionStatus.COLLECTING);
        Opinion opinion = opinion(section, OpinionStatus.DRAFT, CONTENT, null);
        given(sectionAccessGuard.requireParticipantSection(SECTION_ID, USER_ID)).willReturn(section);
        given(opinionRepository.findByProjectSection_IdAndAuthor_Id(SECTION_ID, USER_ID))
                .willReturn(Optional.of(opinion));

        MyOpinionResponse response = opinionService.getMyOpinion(SECTION_ID, USER_ID);

        assertThat(response.exists()).isTrue();
        assertThat(response.id()).isEqualTo(501L);
        assertThat(response.content()).isEqualTo(CONTENT);
        assertThat(response.status()).isEqualTo(OpinionStatus.DRAFT);
        assertThat(response.hasUnsubmittedChanges()).isFalse();
        assertThat(response.submittedAt()).isNull();
    }

    @Test
    @DisplayName("제출 후 재편집 중이면 hasUnsubmittedChanges=true 로 재제출 필요를 알린다")
    void getMyOpinion_flagsUnsubmittedChanges_whileReEditing() {
        ProjectSection section = section(ProjectSectionStatus.COLLECTING);
        LocalDateTime firstSubmittedAt = LocalDateTime.of(2026, 7, 14, 12, 5);
        Opinion opinion = submittedOpinion(501L, section, user(), firstSubmittedAt);
        opinion.updateContent(REVISED_CONTENT); // 재편집 — 제출본은 유지된다
        given(sectionAccessGuard.requireParticipantSection(SECTION_ID, USER_ID)).willReturn(section);
        given(opinionRepository.findByProjectSection_IdAndAuthor_Id(SECTION_ID, USER_ID))
                .willReturn(Optional.of(opinion));

        MyOpinionResponse response = opinionService.getMyOpinion(SECTION_ID, USER_ID);

        assertThat(response.content()).isEqualTo(REVISED_CONTENT);
        assertThat(response.status()).isEqualTo(OpinionStatus.SUBMITTED);
        assertThat(response.hasUnsubmittedChanges()).isTrue();
        assertThat(response.submittedAt()).isEqualTo(firstSubmittedAt);
    }

    @Test
    @DisplayName("아직 의견을 작성하지 않았으면 예외 없이 exists=false 를 반환한다")
    void getMyOpinion_returnsEmpty_whenAbsent() {
        ProjectSection section = section(ProjectSectionStatus.COLLECTING);
        given(sectionAccessGuard.requireParticipantSection(SECTION_ID, USER_ID)).willReturn(section);
        given(opinionRepository.findByProjectSection_IdAndAuthor_Id(SECTION_ID, USER_ID))
                .willReturn(Optional.empty());

        MyOpinionResponse response = opinionService.getMyOpinion(SECTION_ID, USER_ID);

        assertThat(response.exists()).isFalse();
        assertThat(response.id()).isNull();
        assertThat(response.content()).isNull();
        assertThat(response.status()).isNull();
        assertThat(response.updatedAt()).isNull();
    }

    @Test
    @DisplayName("조회 시 섹션이 없거나 비멤버면 SECTION_NOT_FOUND 예외를 그대로 전파한다 (존재 숨김)")
    void getMyOpinion_sectionHiddenOrMissing_throws() {
        given(sectionAccessGuard.requireParticipantSection(SECTION_ID, USER_ID))
                .willThrow(new BusinessException(ErrorCode.SECTION_NOT_FOUND));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> opinionService.getMyOpinion(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SECTION_NOT_FOUND);
    }

    @Test
    @DisplayName("임시저장된 의견을 SUBMITTED 상태로 제출하고 최초 제출 시각을 반환한다")
    void submitMyOpinion_submitsDraft() {
        ProjectSection section = section(ProjectSectionStatus.COLLECTING);
        Opinion opinion = opinion(section, OpinionStatus.DRAFT, CONTENT, null);
        given(sectionAccessGuard.requireParticipantSectionForUpdate(SECTION_ID, USER_ID))
                .willReturn(section);
        given(opinionRepository.findByProjectSection_IdAndAuthor_Id(SECTION_ID, USER_ID))
                .willReturn(Optional.of(opinion));

        OpinionSubmitResponse response = opinionService.submitMyOpinion(SECTION_ID, USER_ID);

        assertThat(response.id()).isEqualTo(501L);
        assertThat(response.submittedAt()).isNotNull();
        assertThat(opinion.getStatus()).isEqualTo(OpinionStatus.SUBMITTED);
        assertThat(opinion.getSubmittedContentOrLegacy()).isEqualTo(CONTENT);
        assertThat(opinion.getSubmittedAt()).isEqualTo(response.submittedAt());
    }

    @Test
    @DisplayName("재편집 후 재제출하면 제출본이 갱신되고 최초 제출 시각은 유지된다 (§4.1)")
    void submitMyOpinion_resubmit_updatesSubmittedContent_keepsFirstSubmittedAt() {
        ProjectSection section = section(ProjectSectionStatus.COLLECTING);
        LocalDateTime firstSubmittedAt = LocalDateTime.of(2026, 7, 14, 12, 5);
        Opinion opinion = submittedOpinion(501L, section, user(), firstSubmittedAt);
        opinion.updateContent(REVISED_CONTENT); // 재편집
        given(sectionAccessGuard.requireParticipantSectionForUpdate(SECTION_ID, USER_ID))
                .willReturn(section);
        given(opinionRepository.findByProjectSection_IdAndAuthor_Id(SECTION_ID, USER_ID))
                .willReturn(Optional.of(opinion));

        OpinionSubmitResponse response = opinionService.submitMyOpinion(SECTION_ID, USER_ID);

        assertThat(opinion.getSubmittedContentOrLegacy()).isEqualTo(REVISED_CONTENT);
        assertThat(opinion.hasUnsubmittedChanges()).isFalse();
        assertThat(response.submittedAt()).isEqualTo(firstSubmittedAt);
        assertThat(opinion.getSubmittedAt()).isEqualTo(firstSubmittedAt);
    }

    @Test
    @DisplayName("작업본 변경이 없는 재호출은 수집 마감 후에도 최초 제출 시각으로 멱등 성공한다")
    void submitMyOpinion_unchangedAfterClose_returnsIdempotentSuccess() {
        ProjectSection section = section(ProjectSectionStatus.SYNTHESIZING);
        LocalDateTime firstSubmittedAt = LocalDateTime.of(2026, 7, 14, 12, 5);
        Opinion opinion = submittedOpinion(501L, section, user(), firstSubmittedAt);
        given(sectionAccessGuard.requireParticipantSectionForUpdate(SECTION_ID, USER_ID))
                .willReturn(section);
        given(opinionRepository.findByProjectSection_IdAndAuthor_Id(SECTION_ID, USER_ID))
                .willReturn(Optional.of(opinion));

        OpinionSubmitResponse response = opinionService.submitMyOpinion(SECTION_ID, USER_ID);

        assertThat(response.submittedAt()).isEqualTo(firstSubmittedAt);
        assertThat(opinion.getSubmittedAt()).isEqualTo(firstSubmittedAt);
    }

    @Test
    @DisplayName("재편집된 작업본의 재제출은 수집이 마감된 섹션에서 거부된다 (§4.4 게이트 잠김)")
    void submitMyOpinion_resubmitAfterClose_throws() {
        ProjectSection section = section(ProjectSectionStatus.SYNTHESIZING);
        Opinion opinion = submittedOpinion(501L, section, user(),
                LocalDateTime.of(2026, 7, 14, 12, 5));
        opinion.updateContent(REVISED_CONTENT); // 마감 전 재편집했지만 재제출은 못 한 상태
        given(sectionAccessGuard.requireParticipantSectionForUpdate(SECTION_ID, USER_ID))
                .willReturn(section);
        given(opinionRepository.findByProjectSection_IdAndAuthor_Id(SECTION_ID, USER_ID))
                .willReturn(Optional.of(opinion));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> opinionService.submitMyOpinion(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.OPINION_COLLECTION_CLOSED);
    }

    @Test
    @DisplayName("미제출 의견은 수집이 마감된 섹션에 제출할 수 없다")
    void submitMyOpinion_collectionClosed_throws() {
        ProjectSection section = section(ProjectSectionStatus.SYNTHESIZING);
        Opinion opinion = opinion(section, OpinionStatus.DRAFT, CONTENT, null);
        given(sectionAccessGuard.requireParticipantSectionForUpdate(SECTION_ID, USER_ID))
                .willReturn(section);
        given(opinionRepository.findByProjectSection_IdAndAuthor_Id(SECTION_ID, USER_ID))
                .willReturn(Optional.of(opinion));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> opinionService.submitMyOpinion(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.OPINION_COLLECTION_CLOSED);
    }

    @Test
    @DisplayName("수집 중이지만 임시저장된 의견이 없으면 OPINION_NOT_FOUND 예외를 던진다")
    void submitMyOpinion_opinionNotFound_throws() {
        ProjectSection section = section(ProjectSectionStatus.COLLECTING);
        given(sectionAccessGuard.requireParticipantSectionForUpdate(SECTION_ID, USER_ID))
                .willReturn(section);
        given(opinionRepository.findByProjectSection_IdAndAuthor_Id(SECTION_ID, USER_ID))
                .willReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> opinionService.submitMyOpinion(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.OPINION_NOT_FOUND);
        assertThat(exception.getErrors()).singleElement().satisfies(error -> {
            assertThat(error.getField()).isEqualTo("projectSectionId");
            assertThat(error.getReason()).isEqualTo("no draft opinion to submit");
        });
    }

    @Test
    @DisplayName("작업본이 제출 기준(20자 이상)을 위반하면 BUSINESS_RULE_VIOLATION 예외를 던진다")
    void submitMyOpinion_invalidContent_throws() {
        ProjectSection section = section(ProjectSectionStatus.COLLECTING);
        Opinion opinion = opinion(section, OpinionStatus.DRAFT, "너무 짧은 의견", null);
        given(sectionAccessGuard.requireParticipantSectionForUpdate(SECTION_ID, USER_ID))
                .willReturn(section);
        given(opinionRepository.findByProjectSection_IdAndAuthor_Id(SECTION_ID, USER_ID))
                .willReturn(Optional.of(opinion));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> opinionService.submitMyOpinion(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
        assertThat(exception.getErrors()).singleElement()
                .satisfies(error -> assertThat(error.getField()).isEqualTo("content"));
    }

    @Test
    @DisplayName("본인이 제출했으면 제출 의견 전체를 제출 시각 순서 그대로 반환한다")
    void getSubmittedOpinions_returnsFullList_whenEverSubmitted() {
        ProjectSection section = section(ProjectSectionStatus.SYNTHESIZING); // 마감 후에도 조회 가능
        User other = user(2L, "이서연");
        Opinion mine = submittedOpinion(501L, section, user(),
                LocalDateTime.of(2026, 7, 14, 10, 20));
        Opinion others = submittedOpinion(508L, section, other,
                LocalDateTime.of(2026, 7, 14, 11, 0));
        given(sectionAccessGuard.requireParticipantSection(SECTION_ID, USER_ID)).willReturn(section);
        given(opinionRepository.findAllWithAuthorByProjectSectionIdAndStatus(
                SECTION_ID, OpinionStatus.SUBMITTED)).willReturn(List.of(mine, others));

        SubmittedOpinionListResponse response = opinionService.getSubmittedOpinions(SECTION_ID, USER_ID);

        assertThat(response.everSubmitted()).isTrue();
        assertThat(response.totalSubmittedCount()).isEqualTo(2);
        assertThat(response.opinions()).hasSize(2);
        assertThat(response.opinions().get(0).id()).isEqualTo(501L);
        assertThat(response.opinions().get(0).author().name()).isEqualTo("김민준");
        assertThat(response.opinions().get(1).id()).isEqualTo(508L);
        assertThat(response.opinions().get(1).author().id()).isEqualTo(2L);
        assertThat(response.opinions().get(1).submittedAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 14, 11, 0));
    }

    @Test
    @DisplayName("목록에는 재편집 중인 작업본이 아니라 제출본이 보인다 (재제출 모델 §4.1)")
    void getSubmittedOpinions_showsSubmittedContent_notWorkingCopy() {
        ProjectSection section = section(ProjectSectionStatus.COLLECTING);
        Opinion mine = submittedOpinion(501L, section, user(),
                LocalDateTime.of(2026, 7, 14, 10, 20));
        mine.updateContent(REVISED_CONTENT); // 재편집 중 — 아직 재제출하지 않음
        given(sectionAccessGuard.requireParticipantSection(SECTION_ID, USER_ID)).willReturn(section);
        given(opinionRepository.findAllWithAuthorByProjectSectionIdAndStatus(
                SECTION_ID, OpinionStatus.SUBMITTED)).willReturn(List.of(mine));

        SubmittedOpinionListResponse response = opinionService.getSubmittedOpinions(SECTION_ID, USER_ID);

        assertThat(response.opinions().get(0).content()).isEqualTo(CONTENT);
    }

    @Test
    @DisplayName("본인이 제출하지 않았으면 목록을 숨기고 제출 건수만 반환한다 (공개 게이트)")
    void getSubmittedOpinions_hidesList_whenNotSubmitted() {
        ProjectSection section = section(ProjectSectionStatus.COLLECTING);
        Opinion others = submittedOpinion(508L, section, user(2L, "이서연"),
                LocalDateTime.of(2026, 7, 14, 11, 0));
        given(sectionAccessGuard.requireParticipantSection(SECTION_ID, USER_ID)).willReturn(section);
        given(opinionRepository.findAllWithAuthorByProjectSectionIdAndStatus(
                SECTION_ID, OpinionStatus.SUBMITTED)).willReturn(List.of(others));

        SubmittedOpinionListResponse response = opinionService.getSubmittedOpinions(SECTION_ID, USER_ID);

        assertThat(response.everSubmitted()).isFalse();
        assertThat(response.totalSubmittedCount()).isEqualTo(1);
        assertThat(response.opinions()).isEmpty();
    }

    @Test
    @DisplayName("목록 조회 시 섹션이 없거나 비멤버면 SECTION_NOT_FOUND 예외를 그대로 전파한다 (존재 숨김)")
    void getSubmittedOpinions_sectionHiddenOrMissing_throws() {
        given(sectionAccessGuard.requireParticipantSection(SECTION_ID, USER_ID))
                .willThrow(new BusinessException(ErrorCode.SECTION_NOT_FOUND));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> opinionService.getSubmittedOpinions(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SECTION_NOT_FOUND);
    }

    @Test
    @DisplayName("OWNER가 제출 의견이 있는 COLLECTING 섹션을 마감하면 SYNTHESIZING 으로 전이한다")
    void closeOpinionGate_closesCollectingSection_whenSubmittedOpinionExists() {
        ProjectSection section = section(ProjectSectionStatus.COLLECTING);
        given(sectionAccessGuard.requireOwnedSectionForUpdate(SECTION_ID, USER_ID)).willReturn(section);
        given(opinionRepository.existsByProjectSection_IdAndStatus(SECTION_ID, OpinionStatus.SUBMITTED))
                .willReturn(true);
        willAnswer(invocation -> {
            section.changeStatus(ProjectSectionStatus.SYNTHESIZING, LocalDateTime.now());
            return null;
        }).given(sectionStatusService).markSynthesizing(SECTION_ID, USER_ID);

        OpinionGateCloseResponse response = opinionService.closeOpinionGate(SECTION_ID, USER_ID);

        assertThat(response.sectionId()).isEqualTo(SECTION_ID);
        assertThat(response.sectionStatus()).isEqualTo(ProjectSectionStatus.SYNTHESIZING);
        assertThat(response.closedAt()).isNotNull();
        verify(sectionStatusService).markSynthesizing(SECTION_ID, USER_ID);
    }

    @Test
    @DisplayName("제출 의견이 없으면 수집 마감은 NO_SUBMITTED_OPINION 예외를 던진다")
    void closeOpinionGate_withoutSubmittedOpinion_throws() {
        ProjectSection section = section(ProjectSectionStatus.COLLECTING);
        given(sectionAccessGuard.requireOwnedSectionForUpdate(SECTION_ID, USER_ID)).willReturn(section);
        given(opinionRepository.existsByProjectSection_IdAndStatus(SECTION_ID, OpinionStatus.SUBMITTED))
                .willReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> opinionService.closeOpinionGate(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NO_SUBMITTED_OPINION);
        verify(sectionStatusService, never()).markSynthesizing(SECTION_ID, USER_ID);
    }

    @Test
    @DisplayName("COLLECTING 이 아닌 섹션의 수집 마감은 INVALID_SECTION_STATUS_TRANSITION 예외를 던진다")
    void closeOpinionGate_invalidStatus_throws() {
        ProjectSection section = section(ProjectSectionStatus.SYNTHESIZING);
        given(sectionAccessGuard.requireOwnedSectionForUpdate(SECTION_ID, USER_ID)).willReturn(section);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> opinionService.closeOpinionGate(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_SECTION_STATUS_TRANSITION);
    }

    @Test
    @DisplayName("OWNER가 미확정 섹션을 재오픈하면 COLLECTING 상태와 stale 정보를 반환한다")
    void reopenOpinionGate_reopensUnconfirmedSection() {
        ProjectSection section = section(ProjectSectionStatus.DRAFTING);
        given(sectionAccessGuard.requireOwnedSectionForUpdate(SECTION_ID, USER_ID)).willReturn(section);
        given(sectionStatusService.markCollecting(SECTION_ID, USER_ID)).willAnswer(invocation -> {
            section.changeStatus(ProjectSectionStatus.COLLECTING, LocalDateTime.now());
            section.markSynthesisStale();
            return section;
        });

        OpinionGateReopenResponse response = opinionService.reopenOpinionGate(SECTION_ID, USER_ID);

        assertThat(response.sectionId()).isEqualTo(SECTION_ID);
        assertThat(response.sectionStatus()).isEqualTo(ProjectSectionStatus.COLLECTING);
        assertThat(response.synthesisStale()).isTrue();
        assertThat(response.reopenedAt()).isNotNull();
        verify(draftLeaseService).releaseOwnLeaseOrRejectOther(SECTION_ID, USER_ID);
        verify(sectionStatusService).markCollecting(SECTION_ID, USER_ID);
    }

    @Test
    @DisplayName("타인이 편집 중이면 S004로 재오픈을 거부하고 상태를 전이하지 않는다")
    void reopenOpinionGate_otherActiveLease_throwsS004() {
        ProjectSection section = section(ProjectSectionStatus.DRAFTING);
        given(sectionAccessGuard.requireOwnedSectionForUpdate(SECTION_ID, USER_ID)).willReturn(section);
        willThrow(new BusinessException(ErrorCode.DRAFT_LEASE_HELD_BY_OTHER))
                .given(draftLeaseService).releaseOwnLeaseOrRejectOther(SECTION_ID, USER_ID);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> opinionService.reopenOpinionGate(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DRAFT_LEASE_HELD_BY_OTHER);
        verify(sectionStatusService, never()).markCollecting(SECTION_ID, USER_ID);
    }

    @Test
    @DisplayName("이미 열린 섹션의 재오픈은 INVALID_SECTION_STATUS_TRANSITION 예외를 던진다")
    void reopenOpinionGate_alreadyOpenOrConfirmed_throws() {
        ProjectSection section = section(ProjectSectionStatus.COLLECTING);
        given(sectionAccessGuard.requireOwnedSectionForUpdate(SECTION_ID, USER_ID)).willReturn(section);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> opinionService.reopenOpinionGate(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_SECTION_STATUS_TRANSITION);
        verifyNoInteractions(draftLeaseService);
        verify(sectionStatusService, never()).markCollecting(SECTION_ID, USER_ID);
    }

    /**
     * 제출까지 마친 의견 픽스처 — {@code submit()} 경유로 제출본까지 채운다.
     */
    private Opinion submittedOpinion(Long id, ProjectSection section, User author,
                                     LocalDateTime submittedAt) {
        Opinion opinion = Opinion.builder()
                .projectSection(section)
                .author(author)
                .content(CONTENT)
                .status(OpinionStatus.DRAFT)
                .build();
        opinion.submit(submittedAt);
        ReflectionTestUtils.setField(opinion, "id", id);
        return opinion;
    }

    private Opinion opinion(ProjectSection section, OpinionStatus status, String content,
                            LocalDateTime submittedAt) {
        Opinion opinion = Opinion.builder()
                .projectSection(section)
                .author(user())
                .content(content)
                .status(status)
                .submittedAt(submittedAt)
                .build();
        ReflectionTestUtils.setField(opinion, "id", 501L);
        return opinion;
    }

    private ProjectSection section(ProjectSectionStatus status) {
        Project project = Project.builder()
                .owner(user())
                .title("발표 프로젝트")
                .status(ProjectStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(project, "id", PROJECT_ID);
        ProjectSection section = ProjectSection.builder()
                .project(project)
                .title("문제 정의")
                .sectionOrder(1)
                .status(status)
                .build();
        ReflectionTestUtils.setField(section, "id", SECTION_ID);
        return section;
    }

    private User user() {
        return user(USER_ID, "김민준");
    }

    private User user(Long id, String name) {
        User user = User.builder().name(name).status(UserStatus.ACTIVE).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
