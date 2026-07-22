package com.wevo.backend.section.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.issue.service.SynthesisSetQueryService;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.domain.SectionStatusHistory;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import com.wevo.backend.section.repository.SectionStatusHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 섹션 상태 전이의 <b>단일 진입점</b>. (스프린트 안건 ⑥ — 전이 로직은 A/section 도메인 소유)
 *
 * <p>다른 도메인(B/C/D)은 자신의 액션 트랜잭션 안에서 이 서비스 메서드를 호출한다.
 * 전이 <b>규칙·권한·이력 기록</b>을 한 곳에서 책임진다. 별도 엔드포인트는 노출하지 않는다.
 *
 * <p>기본 전파(REQUIRED)로 <b>호출자 트랜잭션에 참여</b>하여, 호출측 액션(예: 수집 마감)과
 * 원자적으로 커밋/롤백된다.
 *
 * <p>상태 전이의 OWNER 권한 검사는 ProjectAccessGuard에 위임해 다른 도메인과
 * 동일한 미참여·역할 불일치 예외 규칙을 적용한다.
 */
@Service
public class SectionStatusService {

    private final ProjectSectionRepository projectSectionRepository;
    private final ProjectAccessGuard projectAccessGuard;
    private final SynthesisSetQueryService synthesisSetQueryService;
    private final SectionStatusHistoryRepository sectionStatusHistoryRepository;

    public SectionStatusService(ProjectSectionRepository projectSectionRepository,
                                ProjectAccessGuard projectAccessGuard,
                                SynthesisSetQueryService synthesisSetQueryService,
                                SectionStatusHistoryRepository sectionStatusHistoryRepository) {
        this.projectSectionRepository = projectSectionRepository;
        this.projectAccessGuard = projectAccessGuard;
        this.synthesisSetQueryService = synthesisSetQueryService;
        this.sectionStatusHistoryRepository = sectionStatusHistoryRepository;
    }

    /**
     * 의견 수집 마감 전이. {@code COLLECTING → SYNTHESIZING} (팀장 전용)
     *
     * <p>B(의견 도메인)의 "수집 마감" 처리에서 호출한다. (게이트 CLOSED 전환·제출 검증은 호출측 책임)
     */
    @Transactional
    public void markSynthesizing(Long sectionId, Long actorUserId) {
        transition(sectionId, actorUserId, ProjectSectionStatus.SYNTHESIZING, "COLLECT_CLOSED");
    }

    /**
     * 의견 수집 재오픈 전이. 미확정 섹션을 {@code COLLECTING} 으로 되돌린다. (팀장 전용)
     *
     * <p>재오픈마다 마감 세대를 증가시켜 동일한 의견 집합이어도 이전 AI 작업을 재사용하지 않는다.
     * 기존 정리 세트가 있으면 그 결과가 낡았으므로 재정리 필요 플래그를 남기고,
     * 정리 이력이 없으면 과거 임시 판정으로 남은 stale 값을 해제한다.
     */
    @Transactional
    public ProjectSection markCollecting(Long sectionId, Long actorUserId) {
        ProjectSection section = transition(sectionId, actorUserId,
                ProjectSectionStatus.COLLECTING, "COLLECT_REOPENED");
        section.advanceOpinionGateGeneration();
        if (synthesisSetQueryService.existsForSection(sectionId)) {
            section.markSynthesisStale();
        } else {
            section.clearSynthesisStale();
        }
        return section;
    }

    /**
     * 검토 요청 전이. {@code DRAFTING → REVIEWING} (프로젝트 참여자 전용 — 팀장·팀원 모두)
     *
     * <p>다른 전이(마감·재오픈)와 달리 <b>참여자 전체</b>가 실행할 수 있다 — 정책서 §3.2 전이 표가
     * 마감·결정 반영에는 "팀장이"를 명시하면서 검토 요청에만 주체를 비워뒀고(의도적 구분),
     * 초안 편집이 팀장·팀원 모두 가능(§5.2)하므로 편집을 마친 사람이 바로 요청한다.
     * (정책서 기준 팀 확정 2026-07-18 — API_SPEC §3.7.7)
     *
     * <p>진입 조건(초안 존재·활성 편집자 없음)은 검토 요청 API({@code SectionReviewRequestService})가
     * 검사한다 — 이 메서드는 전이 규칙·권한·이력만 책임진다.
     */
    @Transactional
    public ProjectSection markReviewing(Long sectionId, Long actorUserId) {
        ProjectSection section = requireSection(sectionId);
        ProjectMember actor = requireParticipant(section, actorUserId);
        return applyTransition(section, actor, ProjectSectionStatus.REVIEWING, "REVIEW_REQUESTED");
    }

    /**
     * 공통 전이 처리: 섹션 조회 → 팀장 권한 검증 → 상태 전이(규칙 검증) → 이력 기록.
     */
    private ProjectSection transition(Long sectionId, Long actorUserId,
                                      ProjectSectionStatus target, String eventType) {
        ProjectSection section = requireSection(sectionId);
        ProjectMember actor = requireOwner(section, actorUserId);
        return applyTransition(section, actor, target, eventType);
    }

    /**
     * 권한 검증을 마친 실행자의 전이를 적용하고 이력을 남긴다.
     */
    private ProjectSection applyTransition(ProjectSection section, ProjectMember actor,
                                           ProjectSectionStatus target, String eventType) {
        ProjectSectionStatus from = section.getStatus();
        section.changeStatus(target); // 허용되지 않은 전이면 INVALID_SECTION_STATUS_TRANSITION

        // version(본문 버전)은 초안 이후 전이에서만 의미가 있다. COLLECTING→SYNTHESIZING 시점엔
        // 초안이 없어 null이 정상이며, 초안 단계 전이(DRAFTING→…) 구현 시 contentVersion을 채운다.
        sectionStatusHistoryRepository.save(SectionStatusHistory.builder()
                .projectSection(section)
                .actor(actor.getUser())
                .eventType(eventType)
                .fromStatus(from)
                .toStatus(target)
                .build());
        return section;
    }

    private ProjectSection requireSection(Long sectionId) {
        return projectSectionRepository.findById(sectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SECTION_NOT_FOUND));
    }

    /**
     * 요청자가 해당 섹션 프로젝트의 참여자(OWNER 또는 MEMBER)인지 검증한다.
     * 비멤버는 존재 숨김 규칙에 따라 {@link ErrorCode#SECTION_NOT_FOUND}(404)로 숨긴다.
     */
    private ProjectMember requireParticipant(ProjectSection section, Long actorUserId) {
        try {
            return projectAccessGuard.requireParticipant(section.getProject().getId(), actorUserId);
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.NOT_PROJECT_MEMBER) {
                throw new BusinessException(ErrorCode.SECTION_NOT_FOUND);
            }
            throw e;
        }
    }

    /**
     * 요청자가 해당 섹션 프로젝트의 팀장(OWNER)인지 검증한다.
     *
     * <p>비멤버는 섹션 기반 API의 존재 숨김 규칙(CLAUDE.md §5.6)에 따라
     * {@link ErrorCode#SECTION_NOT_FOUND}(404)로 숨긴다. 역할 부족(403)은 그대로 전파한다.
     *
     * @return 상태 전이 이력의 실행자 정보로 사용할 프로젝트 멤버십
     */
    private ProjectMember requireOwner(ProjectSection section, Long actorUserId) {
        return ProjectAccessGuard.hidingNonMember(ErrorCode.SECTION_NOT_FOUND,
                () -> projectAccessGuard.requireOwner(section.getProject().getId(), actorUserId));
    }
}
