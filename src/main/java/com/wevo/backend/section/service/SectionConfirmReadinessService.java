package com.wevo.backend.section.service;

import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.review.service.ConfirmReviewGate;
import com.wevo.backend.review.service.TeamReviewService;
import com.wevo.backend.section.domain.AiCheckStatus;
import com.wevo.backend.section.domain.ConfirmReadinessCheck;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.dto.response.SectionConfirmReadinessResponse;
import com.wevo.backend.section.dto.response.SectionConfirmReadinessResponse.CheckResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 섹션 확정 가능 여부 조회. (정책서 §6.3)
 *
 * <p>확정 버튼을 그리기 위한 <b>조회 전용</b> 진입점이다. §6.3의 각 조건을 판정해 조건별 충족 여부와
 * 미충족 사유(§6.3.2)를 돌려주고, 상태를 바꾸지 않는다. 실제 확정은 §3.7.6(초안 확정)이 같은
 * 조건을 서버에서 재검증한 뒤 수행한다.
 *
 * <ul>
 *   <li>{@code ready} — 섹션 조건({@link ConfirmReadinessCheck})이 모두 충족됐는가.</li>
 *   <li>{@code canConfirm} — {@code ready}이면서 호출자가 팀장(OWNER)인가 (§6.3 조건 5).
 *       팀원이 조회하면 {@code ready=true}여도 {@code canConfirm=false}다.</li>
 * </ul>
 *
 * <p>팀원 동의·미해결 수정요청 판정은 review 도메인({@link TeamReviewService})이 소유하고,
 * 활성 편집자 판정은 편집 잠금({@link DraftLeaseService})이 소유한다 — 이 서비스는 조건을 조합만 한다.
 */
@Service
@Transactional(readOnly = true)
public class SectionConfirmReadinessService {

    private final SectionAccessGuard sectionAccessGuard;
    private final ProjectAccessGuard projectAccessGuard;
    private final TeamReviewService teamReviewService;
    private final DraftLeaseService draftLeaseService;

    public SectionConfirmReadinessService(SectionAccessGuard sectionAccessGuard,
                                          ProjectAccessGuard projectAccessGuard,
                                          TeamReviewService teamReviewService,
                                          DraftLeaseService draftLeaseService) {
        this.sectionAccessGuard = sectionAccessGuard;
        this.projectAccessGuard = projectAccessGuard;
        this.teamReviewService = teamReviewService;
        this.draftLeaseService = draftLeaseService;
    }

    /**
     * 섹션의 확정 가능 여부를 조회한다. (프로젝트 참여자 전용)
     *
     * @throws com.wevo.backend.global.exception.BusinessException
     *         섹션이 없거나 비멤버면(존재 숨김) {@code S001}
     */
    public SectionConfirmReadinessResponse getReadiness(Long sectionId, Long userId) {
        ProjectSection section = sectionAccessGuard.requireParticipantSection(sectionId, userId);
        Long projectId = section.getProject().getId();

        // canConfirm 판정에 필요한 호출자 역할을 조회한다. requireParticipantSection 이 이미 참여자를
        // 검증하지만 멤버십을 반환하지 않아 역할을 한 번 더 조회한다 — 그 사이 멤버십이 회수되면
        // requireParticipant 는 NOT_PROJECT_MEMBER(403)를 던지므로, requireParticipantSection 과 동일하게
        // 존재 숨김으로 감싸 비멤버 응답을 SECTION_NOT_FOUND(404)로 일관시킨다.
        boolean callerIsOwner = ProjectAccessGuard.hidingNonMember(
                ErrorCode.SECTION_NOT_FOUND,
                () -> projectAccessGuard.requireParticipant(projectId, userId)
        ).getRole() == ProjectMemberRole.OWNER;

        // 팀 검토 관련 두 조건은 같은 목록에서 파생되므로 한 번의 조회로 함께 판정한다.
        ConfirmReviewGate reviewGate = teamReviewService.evaluateConfirmReviewGate(projectId, sectionId);

        List<CheckResponse> checks = List.of(
                CheckResponse.of(ConfirmReadinessCheck.SECTION_REVIEWING,
                        section.getStatus() == ProjectSectionStatus.REVIEWING),
                CheckResponse.of(ConfirmReadinessCheck.AI_CHECK_CURRENT,
                        section.getAiCheckStatus() == AiCheckStatus.CURRENT),
                CheckResponse.of(ConfirmReadinessCheck.MEMBER_APPROVED,
                        reviewGate.memberApprovalSatisfied()),
                CheckResponse.of(ConfirmReadinessCheck.NO_UNRESOLVED_REQUEST,
                        reviewGate.noUnresolvedChangeRequest()),
                CheckResponse.of(ConfirmReadinessCheck.NO_ACTIVE_EDITOR,
                        draftLeaseService.findActiveLease(sectionId).isEmpty())
        );

        return SectionConfirmReadinessResponse.of(callerIsOwner, checks);
    }
}
