package com.wevo.backend.section.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.review.service.ConfirmReviewGate;
import com.wevo.backend.review.service.TeamReviewService;
import com.wevo.backend.section.domain.DraftLease;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.dto.response.SectionConfirmReadinessResponse;
import com.wevo.backend.section.dto.response.SectionConfirmReadinessResponse.CheckResponse;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SectionConfirmReadinessServiceTest {

    private static final Long SECTION_ID = 10L;
    private static final Long PROJECT_ID = 100L;
    private static final Long USER_ID = 1L;

    @Mock
    private SectionAccessGuard sectionAccessGuard;
    @Mock
    private ProjectAccessGuard projectAccessGuard;
    @Mock
    private TeamReviewService teamReviewService;
    @Mock
    private DraftLeaseService draftLeaseService;

    @InjectMocks
    private SectionConfirmReadinessService service;

    @Test
    @DisplayName("모든 조건을 충족하고 호출자가 팀장이면 ready=true·canConfirm=true, 사유가 없다")
    void allConditionsMet_owner_readyAndCanConfirm() {
        stubAllConditionsMet(ProjectMemberRole.OWNER);

        SectionConfirmReadinessResponse response = service.getReadiness(SECTION_ID, USER_ID);

        assertThat(response.ready()).isTrue();
        assertThat(response.canConfirm()).isTrue();
        assertThat(response.checks()).allMatch(CheckResponse::satisfied);
        assertThat(response.checks()).extracting(CheckResponse::reason).containsOnlyNulls();
    }

    @Test
    @DisplayName("조건은 다 충족돼도 팀원이 조회하면 ready=true·canConfirm=false (팀장만 확정)")
    void allConditionsMet_member_readyButCannotConfirm() {
        stubAllConditionsMet(ProjectMemberRole.MEMBER);

        SectionConfirmReadinessResponse response = service.getReadiness(SECTION_ID, USER_ID);

        assertThat(response.ready()).isTrue();
        assertThat(response.canConfirm()).isFalse();
    }

    @Test
    @DisplayName("REVIEWING 이 아니면 SECTION_REVIEWING 미충족 + 사유, ready=false")
    void notReviewing_sectionReviewingUnsatisfied() {
        stub(section(ProjectSectionStatus.DRAFTING, true), ProjectMemberRole.OWNER,
                true, false, Optional.empty());

        SectionConfirmReadinessResponse response = service.getReadiness(SECTION_ID, USER_ID);

        assertThat(response.ready()).isFalse();
        assertThat(response.canConfirm()).isFalse();
        assertThat(reason(response, "SECTION_REVIEWING")).isEqualTo("섹션이 검토 단계가 아닙니다.");
    }

    @Test
    @DisplayName("AI 사전 검토가 없으면(null) AI_CHECK_CURRENT 미충족 + §6.3.2 사유")
    void aiCheckNull_aiCheckCurrentUnsatisfied() {
        stub(section(ProjectSectionStatus.REVIEWING, false), ProjectMemberRole.OWNER,
                true, false, Optional.empty());

        SectionConfirmReadinessResponse response = service.getReadiness(SECTION_ID, USER_ID);

        assertThat(satisfied(response, "AI_CHECK_CURRENT")).isFalse();
        assertThat(reason(response, "AI_CHECK_CURRENT"))
                .isEqualTo("현재 초안에 대한 AI 사전 검토가 필요합니다.");
    }

    @Test
    @DisplayName("팀원 동의가 없으면 MEMBER_APPROVED 미충족 + §6.3.2 사유")
    void noApproval_memberApprovedUnsatisfied() {
        stub(section(ProjectSectionStatus.REVIEWING, true), ProjectMemberRole.OWNER,
                false, false, Optional.empty());

        SectionConfirmReadinessResponse response = service.getReadiness(SECTION_ID, USER_ID);

        assertThat(satisfied(response, "MEMBER_APPROVED")).isFalse();
        assertThat(reason(response, "MEMBER_APPROVED")).isEqualTo("팀원 1명 이상의 동의가 필요합니다.");
    }

    @Test
    @DisplayName("미해결 수정요청이 있으면 NO_UNRESOLVED_REQUEST 미충족 + §6.3.2 사유")
    void unresolvedRequest_noUnresolvedRequestUnsatisfied() {
        stub(section(ProjectSectionStatus.REVIEWING, true), ProjectMemberRole.OWNER,
                true, true, Optional.empty());

        SectionConfirmReadinessResponse response = service.getReadiness(SECTION_ID, USER_ID);

        assertThat(satisfied(response, "NO_UNRESOLVED_REQUEST")).isFalse();
        assertThat(reason(response, "NO_UNRESOLVED_REQUEST")).isEqualTo("미해결 수정요청이 있습니다.");
    }

    @Test
    @DisplayName("활성 편집자가 있으면 NO_ACTIVE_EDITOR 미충족 + §6.3.2 사유")
    void activeEditor_noActiveEditorUnsatisfied() {
        ProjectSection section = section(ProjectSectionStatus.REVIEWING, true);
        DraftLease lease = DraftLease.builder()
                .projectSection(section)
                .holderUserId(2L)
                .leaseUntil(LocalDateTime.now().plusMinutes(3))
                .build();
        stub(section, ProjectMemberRole.OWNER, true, false, Optional.of(lease));

        SectionConfirmReadinessResponse response = service.getReadiness(SECTION_ID, USER_ID);

        assertThat(satisfied(response, "NO_ACTIVE_EDITOR")).isFalse();
        assertThat(reason(response, "NO_ACTIVE_EDITOR")).isEqualTo("현재 초안을 편집 중인 사용자가 있습니다.");
    }

    @Test
    @DisplayName("섹션이 없거나 비멤버면 SECTION_NOT_FOUND 를 그대로 전파한다 (존재 숨김)")
    void hiddenOrMissing_propagatesSectionNotFound() {
        given(sectionAccessGuard.requireParticipantSection(SECTION_ID, USER_ID))
                .willThrow(new BusinessException(ErrorCode.SECTION_NOT_FOUND));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getReadiness(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SECTION_NOT_FOUND);
    }

    // ── 헬퍼 ──

    private void stubAllConditionsMet(ProjectMemberRole role) {
        stub(section(ProjectSectionStatus.REVIEWING, true), role, true, false, Optional.empty());
    }

    private void stub(ProjectSection section, ProjectMemberRole callerRole,
                      boolean memberApprovalSatisfied, boolean hasUnresolvedRequest,
                      Optional<DraftLease> activeLease) {
        given(sectionAccessGuard.requireParticipantSection(SECTION_ID, USER_ID)).willReturn(section);
        given(projectAccessGuard.requireParticipant(PROJECT_ID, USER_ID)).willReturn(member(callerRole));
        given(teamReviewService.evaluateConfirmReviewGate(PROJECT_ID, SECTION_ID))
                .willReturn(new ConfirmReviewGate(memberApprovalSatisfied, !hasUnresolvedRequest));
        given(draftLeaseService.findActiveLease(SECTION_ID)).willReturn(activeLease);
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

    private ProjectMember member(ProjectMemberRole role) {
        return ProjectMember.builder().role(role).joinedAt(LocalDateTime.now()).build();
    }

    private boolean satisfied(SectionConfirmReadinessResponse response, String key) {
        return check(response, key).satisfied();
    }

    private String reason(SectionConfirmReadinessResponse response, String key) {
        return check(response, key).reason();
    }

    private CheckResponse check(SectionConfirmReadinessResponse response, String key) {
        return response.checks().stream()
                .filter(c -> c.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new AssertionError("check not found: " + key));
    }
}
