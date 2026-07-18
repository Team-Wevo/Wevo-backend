package com.wevo.backend.section.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.review.service.ReviewLinkService;
import com.wevo.backend.review.service.TeamReviewService;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.SectionDraft;
import com.wevo.backend.section.dto.request.SectionDraftSaveRequest;
import com.wevo.backend.section.dto.response.SectionDraftSaveResponse;
import com.wevo.backend.section.repository.SectionDraftRepository;
import com.wevo.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 섹션 초안(본문) 저장 로직. (제품 정책서 §6.1 · API_SPEC §1.9)
 *
 * <p>초안은 <b>버전별 이력</b>으로 관리한다 — 저장할 때마다 새 {@link SectionDraft} 행을
 * {@code version = 직전 최신 + 1} 로 append 한다. 확정 본문 버전 추적·검토 만료 판단의 근거가 된다.
 *
 * <p>본문이 바뀌면 이 섹션의 기존 검토는 더 이상 유효하지 않으므로, 저장의 <b>부수효과</b>로
 * 팀 검토와 외부 검토 링크를 모두 만료 처리한다. (§6.1 — 새 본문으로 다시 검토받아야 함)
 *
 * <p>같은 섹션에 대한 동시 저장은 섹션 행 배타 잠금으로 직렬화하고, 클라이언트가 편집을 시작한
 * 기준 버전({@code baseVersion})이 최신과 다르면 409 로 거부해 덮어쓰기(lost update)를 막는다.
 */
@Service
@Transactional(readOnly = true)
public class SectionDraftService {

    private final SectionAccessGuard sectionAccessGuard;
    private final SectionDraftRepository sectionDraftRepository;
    private final UserRepository userRepository;
    private final ReviewLinkService reviewLinkService;
    private final TeamReviewService teamReviewService;

    public SectionDraftService(SectionAccessGuard sectionAccessGuard,
                               SectionDraftRepository sectionDraftRepository,
                               UserRepository userRepository,
                               ReviewLinkService reviewLinkService,
                               TeamReviewService teamReviewService) {
        this.sectionAccessGuard = sectionAccessGuard;
        this.sectionDraftRepository = sectionDraftRepository;
        this.userRepository = userRepository;
        this.reviewLinkService = reviewLinkService;
        this.teamReviewService = teamReviewService;
    }

    /**
     * 섹션 초안을 저장한다. (참여자 전용 — OWNER/MEMBER 공용)
     *
     * <p>섹션 행을 배타 잠금으로 잡아 "최신 버전 조회 → append" 를 직렬화한다. 같은 잠금을
     * 만료 처리(외부 링크·팀 검토)도 사용하므로, 저장과 만료가 원자적으로 커밋된다.
     *
     * @throws BusinessException 섹션 없음/미참여(존재 숨김 규칙, {@code S001}), 초안이 없는
     *                           단계에서의 저장 시도({@code S002}), 기준 버전 불일치({@code C003})
     */
    @Transactional
    public SectionDraftSaveResponse saveDraft(Long sectionId, Long userId, SectionDraftSaveRequest request) {
        ProjectSection section = sectionAccessGuard.requireParticipantSectionForUpdate(sectionId, userId);

        if (!section.getStatus().allowsDraftEditing()) {
            throw new BusinessException(ErrorCode.INVALID_SECTION_STATUS_TRANSITION);
        }

        // 초안이 없으면 최신 버전 0 으로 본다(최초 저장 → version 1).
        int latestVersion = sectionDraftRepository
                .findTopByProjectSection_IdOrderByVersionDesc(sectionId)
                .map(SectionDraft::getVersion)
                .orElse(0);
        if (!request.baseVersion().equals(latestVersion)) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        SectionDraft draft = SectionDraft.builder()
                .projectSection(section)
                .content(request.content())
                .version(latestVersion + 1)
                .lastEditor(userRepository.getReferenceById(userId))
                .build();
        // auditing(updatedAt)을 응답에 싣기 위해 저장 후 flush 한다.
        sectionDraftRepository.saveAndFlush(draft);

        // 본문 수정의 부수효과 — 기존 검토·외부 링크를 만료 처리한다. (첫 실제 저장 시점 = 이 호출)
        reviewLinkService.markSectionLinksOutdated(sectionId);
        teamReviewService.markSectionTeamReviewsOutdated(sectionId);

        return SectionDraftSaveResponse.from(sectionId, draft);
    }
}
