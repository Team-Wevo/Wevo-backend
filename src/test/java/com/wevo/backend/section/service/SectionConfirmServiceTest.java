package com.wevo.backend.section.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.response.FieldError;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.review.service.ConfirmReviewGate;
import com.wevo.backend.review.service.TeamReviewService;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.domain.SectionDraft;
import com.wevo.backend.section.dto.response.SectionConfirmResponse;
import com.wevo.backend.section.repository.SectionDraftRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SectionConfirmServiceTest {

    private static final Long SECTION_ID = 10L;
    private static final Long PROJECT_ID = 100L;
    private static final Long USER_ID = 1L;
    private static final int CONTENT_VERSION = 2;

    @Mock
    private SectionAccessGuard sectionAccessGuard;
    @Mock
    private SectionDraftRepository sectionDraftRepository;
    @Mock
    private TeamReviewService teamReviewService;
    @Mock
    private DraftLeaseService draftLeaseService;
    @Mock
    private SectionStatusService sectionStatusService;

    @InjectMocks
    private SectionConfirmService sectionConfirmService;

    @Test
    @DisplayName("모든 조건 충족 시 현재 본문 버전으로 확정 전이를 위임한다")
    void confirm_allConditionsMet_delegatesTransition() {
        ProjectSection section = section(ProjectSectionStatus.REVIEWING, true);
        given(sectionAccessGuard.requireOwnedSectionForUpdate(SECTION_ID, USER_ID)).willReturn(section);
        allConditionsSatisfied();
        given(sectionDraftRepository.findTopByProjectSection_IdOrderByVersionDesc(SECTION_ID))
                .willReturn(Optional.of(draft(CONTENT_VERSION)));
        given(sectionStatusService.markConfirmed(SECTION_ID, USER_ID, CONTENT_VERSION))
                .willReturn(confirmedSection(CONTENT_VERSION));

        SectionConfirmResponse response = sectionConfirmService.confirm(SECTION_ID, USER_ID);

        assertThat(response.sectionId()).isEqualTo(SECTION_ID);
        assertThat(response.sectionStatus()).isEqualTo(ProjectSectionStatus.CONFIRMED);
        assertThat(response.confirmedVersion()).isEqualTo(CONTENT_VERSION);
        verify(sectionStatusService).markConfirmed(SECTION_ID, USER_ID, CONTENT_VERSION);
    }

    @Test
    @DisplayName("섹션이 REVIEWING 이 아니면 S002 로 거부하고 전이를 위임하지 않는다")
    void confirm_notReviewing_throwsS002() {
        ProjectSection section = section(ProjectSectionStatus.DRAFTING, true);
        given(sectionAccessGuard.requireOwnedSectionForUpdate(SECTION_ID, USER_ID)).willReturn(section);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sectionConfirmService.confirm(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_SECTION_STATUS_TRANSITION);
        verify(sectionStatusService, never()).markConfirmed(anyLong(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("AI 사전 검토가 최신이 아니면 C003 + AI_CHECK_CURRENT 사유로 거부한다")
    void confirm_aiCheckNotCurrent_throwsC003() {
        ProjectSection section = section(ProjectSectionStatus.REVIEWING, false); // aiCheck 미바인딩
        given(sectionAccessGuard.requireOwnedSectionForUpdate(SECTION_ID, USER_ID)).willReturn(section);
        given(teamReviewService.evaluateConfirmReviewGate(PROJECT_ID, SECTION_ID))
                .willReturn(new ConfirmReviewGate(true, true));
        given(draftLeaseService.findActiveLease(SECTION_ID)).willReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sectionConfirmService.confirm(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(exception.getErrors())
                .extracting(FieldError::getField, FieldError::getReason)
                .containsExactly(tuple("AI_CHECK_CURRENT", "현재 초안에 대한 AI 사전 검토가 필요합니다."));
        verify(sectionStatusService, never()).markConfirmed(anyLong(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("여러 조건이 동시에 미충족이면 C003 의 errors 에 모두 담아 거부한다")
    void confirm_multipleUnmet_collectsAll() {
        ProjectSection section = section(ProjectSectionStatus.REVIEWING, false); // aiCheck 미충족
        given(sectionAccessGuard.requireOwnedSectionForUpdate(SECTION_ID, USER_ID)).willReturn(section);
        given(teamReviewService.evaluateConfirmReviewGate(PROJECT_ID, SECTION_ID))
                .willReturn(new ConfirmReviewGate(false, false)); // 동의 없음 + 미해결 수정요청
        given(draftLeaseService.findActiveLease(SECTION_ID))
                .willReturn(Optional.of(activeLease(section))); // 활성 편집자

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sectionConfirmService.confirm(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(exception.getErrors()).extracting(FieldError::getField)
                .containsExactly("AI_CHECK_CURRENT", "MEMBER_APPROVED",
                        "NO_UNRESOLVED_REQUEST", "NO_ACTIVE_EDITOR");
    }

    @Test
    @DisplayName("활성 편집자가 있으면 C003 + NO_ACTIVE_EDITOR 사유로 거부한다")
    void confirm_activeEditor_throwsC003() {
        ProjectSection section = section(ProjectSectionStatus.REVIEWING, true);
        given(sectionAccessGuard.requireOwnedSectionForUpdate(SECTION_ID, USER_ID)).willReturn(section);
        given(teamReviewService.evaluateConfirmReviewGate(PROJECT_ID, SECTION_ID))
                .willReturn(new ConfirmReviewGate(true, true));
        given(draftLeaseService.findActiveLease(SECTION_ID)).willReturn(Optional.of(activeLease(section)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sectionConfirmService.confirm(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(exception.getErrors()).extracting(FieldError::getField)
                .containsExactly("NO_ACTIVE_EDITOR");
    }

    @Test
    @DisplayName("조건은 충족했지만 초안이 없으면 방어적으로 S003 으로 거부한다")
    void confirm_noDraft_throwsS003() {
        ProjectSection section = section(ProjectSectionStatus.REVIEWING, true);
        given(sectionAccessGuard.requireOwnedSectionForUpdate(SECTION_ID, USER_ID)).willReturn(section);
        allConditionsSatisfied();
        given(sectionDraftRepository.findTopByProjectSection_IdOrderByVersionDesc(SECTION_ID))
                .willReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sectionConfirmService.confirm(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SECTION_DRAFT_NOT_FOUND);
        verify(sectionStatusService, never()).markConfirmed(anyLong(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("OWNER 가 아니면 접근 가드의 FORBIDDEN 을 그대로 전파한다")
    void confirm_notOwner_propagatesForbidden() {
        given(sectionAccessGuard.requireOwnedSectionForUpdate(SECTION_ID, USER_ID))
                .willThrow(new BusinessException(ErrorCode.FORBIDDEN));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sectionConfirmService.confirm(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("섹션이 없거나 비멤버면 SECTION_NOT_FOUND 를 그대로 전파한다 (존재 숨김)")
    void confirm_hiddenOrMissing_propagatesNotFound() {
        given(sectionAccessGuard.requireOwnedSectionForUpdate(SECTION_ID, USER_ID))
                .willThrow(new BusinessException(ErrorCode.SECTION_NOT_FOUND));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sectionConfirmService.confirm(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SECTION_NOT_FOUND);
    }

    // ── 픽스처 ──

    private void allConditionsSatisfied() {
        given(teamReviewService.evaluateConfirmReviewGate(PROJECT_ID, SECTION_ID))
                .willReturn(new ConfirmReviewGate(true, true));
        given(draftLeaseService.findActiveLease(SECTION_ID)).willReturn(Optional.empty());
    }

    private ProjectSection section(ProjectSectionStatus status, boolean aiCheckCurrent) {
        Project project = Project.builder().title("발표 프로젝트").status(ProjectStatus.ACTIVE).build();
        ReflectionTestUtils.setField(project, "id", PROJECT_ID);
        ProjectSection section = ProjectSection.builder()
                .project(project)
                .title("문제 정의")
                .sectionOrder(1)
                .status(status)
                .build();
        ReflectionTestUtils.setField(section, "id", SECTION_ID);
        if (aiCheckCurrent) {
            section.bindCurrentAiCheck();
        }
        return section;
    }

    private ProjectSection confirmedSection(int confirmedVersion) {
        Project project = Project.builder().title("발표 프로젝트").status(ProjectStatus.ACTIVE).build();
        ReflectionTestUtils.setField(project, "id", PROJECT_ID);
        ProjectSection section = ProjectSection.builder()
                .project(project)
                .title("문제 정의")
                .sectionOrder(1)
                .status(ProjectSectionStatus.CONFIRMED)
                .confirmedVersion(confirmedVersion)
                .build();
        ReflectionTestUtils.setField(section, "id", SECTION_ID);
        return section;
    }

    private SectionDraft draft(int version) {
        return SectionDraft.builder().content("본문 v" + version).version(version).build();
    }

    private com.wevo.backend.section.domain.DraftLease activeLease(ProjectSection section) {
        return com.wevo.backend.section.domain.DraftLease.builder()
                .projectSection(section)
                .holderUserId(9L)
                .leaseUntil(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")).plusHours(1))
                .build();
    }
}
