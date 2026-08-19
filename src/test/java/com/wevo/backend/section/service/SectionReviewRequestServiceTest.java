package com.wevo.backend.section.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.section.domain.AiCheckStatus;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.dto.response.SectionReviewRequestResponse;
import com.wevo.backend.section.repository.SectionDraftRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SectionReviewRequestServiceTest {

    private static final Long SECTION_ID = 10L;
    private static final Long PROJECT_ID = 100L;
    private static final Long USER_ID = 1L;

    @Mock
    private SectionAccessGuard sectionAccessGuard;
    @Mock
    private SectionDraftRepository sectionDraftRepository;
    @Mock
    private DraftLeaseService draftLeaseService;
    @Mock
    private SectionStatusService sectionStatusService;

    @InjectMocks
    private SectionReviewRequestService sectionReviewRequestService;

    @Test
    @DisplayName("DRAFTING 섹션에 초안이 있고 편집자가 없으면 REVIEWING 전이를 위임한다")
    void request_transitionsToReviewing() {
        ProjectSection section = section(ProjectSectionStatus.DRAFTING);
        ProjectSection reviewing = section(ProjectSectionStatus.REVIEWING);
        given(sectionAccessGuard.requireParticipantSectionForUpdate(SECTION_ID, USER_ID))
                .willReturn(section);
        given(sectionDraftRepository.existsByProjectSection_Id(SECTION_ID)).willReturn(true);
        given(sectionStatusService.markReviewing(SECTION_ID, USER_ID)).willReturn(reviewing);

        SectionReviewRequestResponse response = sectionReviewRequestService.request(SECTION_ID, USER_ID);

        assertThat(response.sectionId()).isEqualTo(SECTION_ID);
        assertThat(response.sectionStatus()).isEqualTo(ProjectSectionStatus.REVIEWING);
        verify(draftLeaseService).releaseOwnLeaseOrRejectOther(SECTION_ID, USER_ID);
    }

    @Test
    @DisplayName("검토 요청 전에 호출자의 lease 정리를 공통 서비스에 위임한다")
    void request_delegatesLeaseHandling() {
        ProjectSection section = section(ProjectSectionStatus.DRAFTING);
        given(sectionAccessGuard.requireParticipantSectionForUpdate(SECTION_ID, USER_ID))
                .willReturn(section);
        given(sectionDraftRepository.existsByProjectSection_Id(SECTION_ID)).willReturn(true);
        given(sectionStatusService.markReviewing(SECTION_ID, USER_ID))
                .willReturn(section(ProjectSectionStatus.REVIEWING));

        sectionReviewRequestService.request(SECTION_ID, USER_ID);

        verify(draftLeaseService).releaseOwnLeaseOrRejectOther(SECTION_ID, USER_ID);
        verify(sectionStatusService).markReviewing(SECTION_ID, USER_ID);
    }

    @Test
    @DisplayName("타인이 편집 중이면 S004 로 거부한다 (검토 대상 본문 고정)")
    void request_otherActiveEditor_throws() {
        ProjectSection section = section(ProjectSectionStatus.DRAFTING);
        given(sectionAccessGuard.requireParticipantSectionForUpdate(SECTION_ID, USER_ID))
                .willReturn(section);
        given(sectionDraftRepository.existsByProjectSection_Id(SECTION_ID)).willReturn(true);
        willThrow(new BusinessException(ErrorCode.DRAFT_LEASE_HELD_BY_OTHER))
                .given(draftLeaseService).releaseOwnLeaseOrRejectOther(SECTION_ID, USER_ID);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sectionReviewRequestService.request(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DRAFT_LEASE_HELD_BY_OTHER);
        verify(sectionStatusService, never()).markReviewing(anyLong(), anyLong());
    }

    @Test
    @DisplayName("만료된 타인 lease 는 편집자로 보지 않는다 — 요청이 진행된다")
    void request_expiredLease_isIgnored() {
        ProjectSection section = section(ProjectSectionStatus.DRAFTING);
        given(sectionAccessGuard.requireParticipantSectionForUpdate(SECTION_ID, USER_ID))
                .willReturn(section);
        given(sectionDraftRepository.existsByProjectSection_Id(SECTION_ID)).willReturn(true);
        given(sectionStatusService.markReviewing(SECTION_ID, USER_ID))
                .willReturn(section(ProjectSectionStatus.REVIEWING));

        SectionReviewRequestResponse response = sectionReviewRequestService.request(SECTION_ID, USER_ID);

        assertThat(response.sectionStatus()).isEqualTo(ProjectSectionStatus.REVIEWING);
    }

    @Test
    @DisplayName("DRAFTING 이 아닌 섹션은 S002 로 거부한다")
    void request_notDrafting_throws() {
        ProjectSection section = section(ProjectSectionStatus.COLLECTING);
        given(sectionAccessGuard.requireParticipantSectionForUpdate(SECTION_ID, USER_ID))
                .willReturn(section);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sectionReviewRequestService.request(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_SECTION_STATUS_TRANSITION);
        verify(sectionStatusService, never()).markReviewing(anyLong(), anyLong());
    }

    @Test
    @DisplayName("AI 사전 검토가 최신이 아니면 S006 으로 거부한다 (확정 불가 상태 진입 방지)")
    void request_aiCheckNotCurrent_throws() {
        ProjectSection section = section(ProjectSectionStatus.DRAFTING);
        ReflectionTestUtils.setField(section, "aiCheckStatus", AiCheckStatus.OUTDATED);
        given(sectionAccessGuard.requireParticipantSectionForUpdate(SECTION_ID, USER_ID))
                .willReturn(section);
        given(sectionDraftRepository.existsByProjectSection_Id(SECTION_ID)).willReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sectionReviewRequestService.request(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SECTION_AI_PRECHECK_REQUIRED);
        // 거부됐으므로 lease 정리도 전이도 일어나지 않는다.
        verify(draftLeaseService, never()).releaseOwnLeaseOrRejectOther(anyLong(), anyLong());
        verify(sectionStatusService, never()).markReviewing(anyLong(), anyLong());
    }

    @Test
    @DisplayName("초안이 없으면 S003 으로 거부한다")
    void request_withoutDraft_throws() {
        ProjectSection section = section(ProjectSectionStatus.DRAFTING);
        given(sectionAccessGuard.requireParticipantSectionForUpdate(SECTION_ID, USER_ID))
                .willReturn(section);
        given(sectionDraftRepository.existsByProjectSection_Id(SECTION_ID)).willReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sectionReviewRequestService.request(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SECTION_DRAFT_NOT_FOUND);
    }

    @Test
    @DisplayName("섹션이 없거나 비멤버면 SECTION_NOT_FOUND 를 그대로 전파한다 (존재 숨김)")
    void request_hiddenOrMissing_throws() {
        given(sectionAccessGuard.requireParticipantSectionForUpdate(SECTION_ID, USER_ID))
                .willThrow(new BusinessException(ErrorCode.SECTION_NOT_FOUND));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sectionReviewRequestService.request(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SECTION_NOT_FOUND);
    }

    // ── 픽스처 ──

    private ProjectSection section(ProjectSectionStatus status) {
        Project project = Project.builder().title("발표 프로젝트").status(ProjectStatus.ACTIVE).build();
        ReflectionTestUtils.setField(project, "id", PROJECT_ID);
        ProjectSection section = ProjectSection.builder()
                .project(project)
                .title("문제 정의")
                .sectionOrder(1)
                .status(status)
                .build();
        ReflectionTestUtils.setField(section, "id", SECTION_ID);
        // 검토 요청 진입 조건상 AI 사전 검토가 최신이어야 하므로 픽스처 기본값을 CURRENT 로 둔다. (#324)
        ReflectionTestUtils.setField(section, "aiCheckStatus", AiCheckStatus.CURRENT);
        return section;
    }

}
