package com.wevo.backend.section.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.section.domain.AiCheckStatus;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.dto.response.SectionReviewRequestResponse;
import com.wevo.backend.section.repository.SectionDraftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 검토 요청 — 초안 작성이 끝난 섹션을 검토 단계로 보낸다. (API_SPEC §3.7.7, 정책서 §3.2)
 *
 * <p>{@code DRAFTING → REVIEWING} 전이의 진입 조건을 검사한 뒤 전이는
 * {@link SectionStatusService}(section 도메인 단일 진입점)에 위임한다.
 *
 * <ul>
 *   <li><b>권한</b>: 프로젝트 참여자 전체 — 초안 편집이 팀장·팀원 모두 가능(§5.2)하므로
 *       편집을 마친 사람이 바로 요청한다. (정책서 기준 팀 확정 2026-07-18)</li>
 *   <li><b>진입 조건</b>: 섹션이 {@code DRAFTING} + 초안 존재 + 활성 편집자 없음
 *       (검토 대상 본문을 고정하기 위해 — 검토자들이 읽는 본문이 흔들리면 안 된다)</li>
 *   <li><b>팀 검토 PENDING 초기화</b>: 파생 모델(§3.5.6 — 조회 시점 멤버 기준)이라 레코드를
 *       만들지 않는다. 재오픈을 거친 재요청 시 남아 있는 이전 사이클 검토는 본문 저장이 이미
 *       {@code OUTDATED} 처리했으므로 그대로 둔다.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class SectionReviewRequestService {

    private final SectionAccessGuard sectionAccessGuard;
    private final SectionDraftRepository sectionDraftRepository;
    private final DraftLeaseService draftLeaseService;
    private final SectionStatusService sectionStatusService;

    public SectionReviewRequestService(SectionAccessGuard sectionAccessGuard,
                                       SectionDraftRepository sectionDraftRepository,
                                       DraftLeaseService draftLeaseService,
                                       SectionStatusService sectionStatusService) {
        this.sectionAccessGuard = sectionAccessGuard;
        this.sectionDraftRepository = sectionDraftRepository;
        this.draftLeaseService = draftLeaseService;
        this.sectionStatusService = sectionStatusService;
    }

    /**
     * 검토를 요청한다. (프로젝트 참여자 전용)
     *
     * <p>섹션 행을 배타 잠금으로 잡아 초안 저장·lease 획득과 직렬화한다 — 조건 검사와 전이
     * 사이에 다른 요청이 끼어들지 않는다.
     *
     * <p><b>호출자 본인이 보유한 lease는 요청과 함께 원자적으로 해제</b>한다(본인 행위 —
     * 재오픈 §3.4.6의 lease 규칙과 대칭). 타인이 편집 중일 때만 거부한다.
     *
     * @throws BusinessException 섹션 없음/비멤버(존재 숨김 {@code S001}),
     *                           {@code DRAFTING}이 아님({@code S002}), 초안 없음({@code S003}),
     *                           AI 사전 검토 미완료({@code S006}), 타인이 편집 중({@code S004})
     */
    @Transactional
    public SectionReviewRequestResponse request(Long sectionId, Long userId) {
        ProjectSection section =
                sectionAccessGuard.requireParticipantSectionForUpdate(sectionId, userId);

        if (section.getStatus() != ProjectSectionStatus.DRAFTING) {
            throw new BusinessException(ErrorCode.INVALID_SECTION_STATUS_TRANSITION);
        }
        if (!sectionDraftRepository.existsByProjectSection_Id(sectionId)) {
            throw new BusinessException(ErrorCode.SECTION_DRAFT_NOT_FOUND);
        }
        // AI 사전 검토가 현재 본문 기준 최신이어야 검토를 요청할 수 있다. 확정(§6.3)이 이미
        // AI_CHECK_CURRENT 를 필수로 요구하므로, 그 조건을 진입 단계에서 함께 강제해 사전 검토가
        // 완료되지 않은 채 REVIEWING 으로 넘어가 확정이 막히는 상태를 만들지 않는다. (#324)
        // aiCheckStatus 는 성공한 사전 검토가 없으면 null 이므로 null·OUTDATED 를 함께 거른다.
        if (section.getAiCheckStatus() != AiCheckStatus.CURRENT) {
            throw new BusinessException(ErrorCode.SECTION_AI_PRECHECK_REQUIRED);
        }
        draftLeaseService.releaseOwnLeaseOrRejectOther(sectionId, userId);

        ProjectSection transitioned = sectionStatusService.markReviewing(sectionId, userId);
        return SectionReviewRequestResponse.from(transitioned);
    }
}
