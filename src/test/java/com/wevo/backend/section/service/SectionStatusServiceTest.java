package com.wevo.backend.section.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.issue.service.SynthesisSetQueryService;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.domain.SectionStatusHistory;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import com.wevo.backend.section.repository.SectionStatusHistoryRepository;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SectionStatusServiceTest {

    private static final Long SECTION_ID = 50L;
    private static final Long PROJECT_ID = 10L;
    private static final Long OWNER_ID = 1L;

    @Mock
    private ProjectSectionRepository projectSectionRepository;
    @Mock
    private ProjectAccessGuard projectAccessGuard;
    @Mock
    private SynthesisSetQueryService synthesisSetQueryService;
    @Mock
    private SectionStatusHistoryRepository sectionStatusHistoryRepository;

    @InjectMocks
    private SectionStatusService sectionStatusService;

    @Test
    @DisplayName("팀장이 COLLECTING 섹션을 마감하면 SYNTHESIZING 으로 전이되고 이력이 기록된다")
    void markSynthesizing_fromCollectingByOwner_transitionsAndRecordsHistory() {
        User owner = user(OWNER_ID);
        ProjectSection section = section(ProjectSectionStatus.COLLECTING);
        given(projectSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(projectAccessGuard.requireOwner(PROJECT_ID, OWNER_ID))
                .willReturn(member(owner, ProjectMemberRole.OWNER, section.getProject()));

        sectionStatusService.markSynthesizing(SECTION_ID, OWNER_ID);

        assertThat(section.getStatus()).isEqualTo(ProjectSectionStatus.SYNTHESIZING);

        ArgumentCaptor<SectionStatusHistory> captor = ArgumentCaptor.forClass(SectionStatusHistory.class);
        verify(sectionStatusHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getFromStatus()).isEqualTo(ProjectSectionStatus.COLLECTING);
        assertThat(captor.getValue().getToStatus()).isEqualTo(ProjectSectionStatus.SYNTHESIZING);
        assertThat(captor.getValue().getEventType()).isEqualTo("COLLECT_CLOSED");
        assertThat(captor.getValue().getActor()).isEqualTo(owner);
    }

    @Test
    @DisplayName("이미 DRAFTING 인 섹션을 마감 전이하면 INVALID_SECTION_STATUS_TRANSITION 을 던진다")
    void markSynthesizing_invalidFromStatus_throws() {
        User owner = user(OWNER_ID);
        ProjectSection section = section(ProjectSectionStatus.DRAFTING);
        given(projectSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(projectAccessGuard.requireOwner(PROJECT_ID, OWNER_ID))
                .willReturn(member(owner, ProjectMemberRole.OWNER, section.getProject()));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sectionStatusService.markSynthesizing(SECTION_ID, OWNER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_SECTION_STATUS_TRANSITION);
        assertThat(section.getStatus()).isEqualTo(ProjectSectionStatus.DRAFTING); // 변경되지 않음
        verify(sectionStatusHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("팀장이 아니면(MEMBER) 마감 전이 시 FORBIDDEN 을 던진다")
    void markSynthesizing_notOwner_throwsForbidden() {
        ProjectSection section = section(ProjectSectionStatus.COLLECTING);
        given(projectSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(projectAccessGuard.requireOwner(PROJECT_ID, OWNER_ID))
                .willThrow(new BusinessException(ErrorCode.FORBIDDEN));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sectionStatusService.markSynthesizing(SECTION_ID, OWNER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        verify(sectionStatusHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("프로젝트 멤버가 아니면 SECTION_NOT_FOUND 로 숨긴다 (존재 숨김 — CLAUDE.md §5.6)")
    void markSynthesizing_notMember_hiddenAsNotFound() {
        ProjectSection section = section(ProjectSectionStatus.COLLECTING);
        given(projectSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(projectAccessGuard.requireOwner(PROJECT_ID, OWNER_ID))
                .willThrow(new BusinessException(ErrorCode.NOT_PROJECT_MEMBER));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sectionStatusService.markSynthesizing(SECTION_ID, OWNER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SECTION_NOT_FOUND);
    }

    @Test
    @DisplayName("섹션이 없으면 SECTION_NOT_FOUND 를 던진다")
    void markSynthesizing_sectionNotFound_throws() {
        given(projectSectionRepository.findById(SECTION_ID)).willReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sectionStatusService.markSynthesizing(SECTION_ID, OWNER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SECTION_NOT_FOUND);
    }

    @Test
    @DisplayName("기존 정리가 있는 미확정 섹션을 재오픈하면 세대 증가와 stale 표시를 남긴다")
    void markCollecting_fromDrafting_advancesGenerationAndMarksStale() {
        User owner = user(OWNER_ID);
        ProjectSection section = section(ProjectSectionStatus.DRAFTING);
        given(projectSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(projectAccessGuard.requireOwner(PROJECT_ID, OWNER_ID))
                .willReturn(member(owner, ProjectMemberRole.OWNER, section.getProject()));
        given(synthesisSetQueryService.existsForSection(SECTION_ID)).willReturn(true);

        ProjectSection reopened = sectionStatusService.markCollecting(SECTION_ID, OWNER_ID);

        assertThat(reopened.getStatus()).isEqualTo(ProjectSectionStatus.COLLECTING);
        assertThat(reopened.isSynthesisStale()).isTrue();
        assertThat(reopened.getOpinionGateGeneration()).isEqualTo(1);
        ArgumentCaptor<SectionStatusHistory> captor = ArgumentCaptor.forClass(SectionStatusHistory.class);
        verify(sectionStatusHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("COLLECT_REOPENED");
        assertThat(captor.getValue().getFromStatus()).isEqualTo(ProjectSectionStatus.DRAFTING);
        assertThat(captor.getValue().getToStatus()).isEqualTo(ProjectSectionStatus.COLLECTING);
    }

    @Test
    @DisplayName("정리 이력이 없으면 재오픈 세대는 증가하고 기존 synthesisStale은 false로 정정한다")
    void markCollecting_withoutSynthesis_advancesGenerationAndClearsLegacyStale() {
        User owner = user(OWNER_ID);
        ProjectSection section = section(ProjectSectionStatus.SYNTHESIZING);
        section.markSynthesisStale();
        given(projectSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(projectAccessGuard.requireOwner(PROJECT_ID, OWNER_ID))
                .willReturn(member(owner, ProjectMemberRole.OWNER, section.getProject()));
        given(synthesisSetQueryService.existsForSection(SECTION_ID)).willReturn(false);

        ProjectSection reopened = sectionStatusService.markCollecting(SECTION_ID, OWNER_ID);

        assertThat(reopened.getOpinionGateGeneration()).isEqualTo(1);
        assertThat(reopened.isSynthesisStale()).isFalse();
    }

    @Test
    @DisplayName("CONFIRMED 섹션을 재오픈하면 INVALID_SECTION_STATUS_TRANSITION 을 던진다")
    void markCollecting_fromConfirmed_throws() {
        User owner = user(OWNER_ID);
        ProjectSection section = section(ProjectSectionStatus.CONFIRMED);
        given(projectSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(projectAccessGuard.requireOwner(PROJECT_ID, OWNER_ID))
                .willReturn(member(owner, ProjectMemberRole.OWNER, section.getProject()));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sectionStatusService.markCollecting(SECTION_ID, OWNER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_SECTION_STATUS_TRANSITION);
        assertThat(section.getStatus()).isEqualTo(ProjectSectionStatus.CONFIRMED);
        verify(sectionStatusHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("검토 요청 전이는 팀원(MEMBER)도 실행할 수 있다 — DRAFTING → REVIEWING + 이력 기록")
    void markReviewing_memberCanRequest_transitionsAndRecordsHistory() {
        User member = user(2L);
        ProjectSection section = section(ProjectSectionStatus.DRAFTING);
        given(projectSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(projectAccessGuard.requireParticipant(PROJECT_ID, 2L))
                .willReturn(member(member, ProjectMemberRole.MEMBER, section.getProject()));

        ProjectSection transitioned = sectionStatusService.markReviewing(SECTION_ID, 2L);

        assertThat(transitioned.getStatus()).isEqualTo(ProjectSectionStatus.REVIEWING);
        ArgumentCaptor<SectionStatusHistory> captor = ArgumentCaptor.forClass(SectionStatusHistory.class);
        verify(sectionStatusHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("REVIEW_REQUESTED");
        assertThat(captor.getValue().getFromStatus()).isEqualTo(ProjectSectionStatus.DRAFTING);
        assertThat(captor.getValue().getToStatus()).isEqualTo(ProjectSectionStatus.REVIEWING);
        assertThat(captor.getValue().getActor()).isEqualTo(member);
    }

    @Test
    @DisplayName("검토 요청 전이 시 비멤버는 SECTION_NOT_FOUND 로 숨긴다 (존재 숨김)")
    void markReviewing_nonMember_hiddenAsNotFound() {
        ProjectSection section = section(ProjectSectionStatus.DRAFTING);
        given(projectSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(projectAccessGuard.requireParticipant(PROJECT_ID, 9L))
                .willThrow(new BusinessException(ErrorCode.NOT_PROJECT_MEMBER));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sectionStatusService.markReviewing(SECTION_ID, 9L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SECTION_NOT_FOUND);
        verify(sectionStatusHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("DRAFTING 이 아닌 섹션의 검토 요청 전이는 S002 로 거부된다")
    void markReviewing_notDrafting_throws() {
        User owner = user(OWNER_ID);
        ProjectSection section = section(ProjectSectionStatus.COLLECTING);
        given(projectSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(projectAccessGuard.requireParticipant(PROJECT_ID, OWNER_ID))
                .willReturn(member(owner, ProjectMemberRole.OWNER, section.getProject()));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sectionStatusService.markReviewing(SECTION_ID, OWNER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_SECTION_STATUS_TRANSITION);
        assertThat(section.getStatus()).isEqualTo(ProjectSectionStatus.COLLECTING);
    }

    @Test
    @DisplayName("팀장이 REVIEWING 섹션을 확정하면 CONFIRMED 전이 + 확정 버전 기록 + 드리프트 해소 + 이력")
    void markConfirmed_fromReviewingByOwner_recordsVersionAndClearsDrift() {
        User owner = user(OWNER_ID);
        ProjectSection section = section(ProjectSectionStatus.REVIEWING);
        section.markDriftReviewRequired(); // 확정으로 해소되는지 확인하기 위해 드리프트를 걸어 둔다
        given(projectSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(projectAccessGuard.requireOwner(PROJECT_ID, OWNER_ID))
                .willReturn(member(owner, ProjectMemberRole.OWNER, section.getProject()));

        ProjectSection confirmed = sectionStatusService.markConfirmed(SECTION_ID, OWNER_ID, 3);

        assertThat(confirmed.getStatus()).isEqualTo(ProjectSectionStatus.CONFIRMED);
        assertThat(confirmed.getConfirmedVersion()).isEqualTo(3);
        assertThat(confirmed.getDriftStatus()).isEqualTo(com.wevo.backend.section.domain.DriftStatus.NONE);

        ArgumentCaptor<SectionStatusHistory> captor = ArgumentCaptor.forClass(SectionStatusHistory.class);
        verify(sectionStatusHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("CONFIRMED");
        assertThat(captor.getValue().getFromStatus()).isEqualTo(ProjectSectionStatus.REVIEWING);
        assertThat(captor.getValue().getToStatus()).isEqualTo(ProjectSectionStatus.CONFIRMED);
        assertThat(captor.getValue().getVersion()).isEqualTo(3);
        assertThat(captor.getValue().getActor()).isEqualTo(owner);
    }

    @Test
    @DisplayName("REVIEWING 이 아닌 섹션의 확정 전이는 S002 로 거부되고 이력을 남기지 않는다")
    void markConfirmed_notReviewing_throws() {
        User owner = user(OWNER_ID);
        ProjectSection section = section(ProjectSectionStatus.DRAFTING);
        given(projectSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(projectAccessGuard.requireOwner(PROJECT_ID, OWNER_ID))
                .willReturn(member(owner, ProjectMemberRole.OWNER, section.getProject()));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sectionStatusService.markConfirmed(SECTION_ID, OWNER_ID, 1));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_SECTION_STATUS_TRANSITION);
        assertThat(section.getStatus()).isEqualTo(ProjectSectionStatus.DRAFTING);
        verify(sectionStatusHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("확정 본문 직접 변경은 REVIEWING 복귀와 변경 version 이력을 기록한다")
    void confirmedContentChangeReturnsToReviewingWithHistory() {
        User actorUser = user(OWNER_ID);
        ProjectSection section = section(ProjectSectionStatus.CONFIRMED);
        ProjectMember actor = member(actorUser, ProjectMemberRole.OWNER, section.getProject());

        sectionStatusService.markReviewingAfterConfirmedContentChange(section, actor, 4);

        assertThat(section.getStatus()).isEqualTo(ProjectSectionStatus.REVIEWING);
        ArgumentCaptor<SectionStatusHistory> captor =
                ArgumentCaptor.forClass(SectionStatusHistory.class);
        verify(sectionStatusHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("CONFIRMED_CONTENT_CHANGED");
        assertThat(captor.getValue().getVersion()).isEqualTo(4);
        assertThat(captor.getValue().getActor()).isEqualTo(actorUser);
    }

    @Test
    @DisplayName("상위 변경으로 확정 하위가 복귀하면 prerequisite 이벤트를 구분해 기록한다")
    void prerequisiteChangeReturnsConfirmedDependentToReviewingWithHistory() {
        User actorUser = user(OWNER_ID);
        ProjectSection section = section(ProjectSectionStatus.CONFIRMED);
        ProjectMember actor = member(actorUser, ProjectMemberRole.OWNER, section.getProject());
        java.time.LocalDateTime activityBefore = section.getLastActivityAt();

        sectionStatusService.markReviewingAfterPrerequisiteChange(section, actor, 3);

        assertThat(section.getStatus()).isEqualTo(ProjectSectionStatus.REVIEWING);
        // 상위 캐스케이드는 사람이 이 하위 섹션을 만진 게 아니므로 활동 시각을 끌어올리지 않는다. (#292)
        assertThat(section.getLastActivityAt()).isEqualTo(activityBefore);
        ArgumentCaptor<SectionStatusHistory> captor =
                ArgumentCaptor.forClass(SectionStatusHistory.class);
        verify(sectionStatusHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("PREREQUISITE_CHANGED");
        assertThat(captor.getValue().getVersion()).isEqualTo(3);
    }

    // ── 픽스처 ──

    private User user(Long id) {
        User user = User.builder().name("호석").status(UserStatus.ACTIVE).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private ProjectSection section(ProjectSectionStatus status) {
        Project project = Project.builder()
                .title("p").resultType(OutputType.PRESENTATION).status(ProjectStatus.ACTIVE).build();
        ReflectionTestUtils.setField(project, "id", PROJECT_ID);
        return ProjectSection.builder()
                .project(project).title("문제 정의").sectionOrder(1)
                .status(status).build();
    }

    private ProjectMember member(User user, ProjectMemberRole role, Project project) {
        return ProjectMember.builder().project(project).user(user).role(role).build();
    }
}
