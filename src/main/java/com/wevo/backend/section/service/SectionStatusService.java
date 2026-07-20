package com.wevo.backend.section.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.domain.SectionStatusHistory;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import com.wevo.backend.section.repository.SectionDraftRepository;
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
    private final SectionDraftRepository sectionDraftRepository;
    private final SectionStatusHistoryRepository sectionStatusHistoryRepository;

    public SectionStatusService(ProjectSectionRepository projectSectionRepository,
                                ProjectAccessGuard projectAccessGuard,
                                SectionDraftRepository sectionDraftRepository,
                                SectionStatusHistoryRepository sectionStatusHistoryRepository) {
        this.projectSectionRepository = projectSectionRepository;
        this.projectAccessGuard = projectAccessGuard;
        this.sectionDraftRepository = sectionDraftRepository;
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
     * <p>기존 초안이 있으면 의견 변경으로 해당 산출물이 낡을 수 있으므로 재정리 필요 플래그를 남긴다.
     * 아직 초안이 없는 정리 단계에서 재오픈할 때는 stale 대상이 없어 기존 값을 유지한다.
     */
    @Transactional
    public ProjectSection markCollecting(Long sectionId, Long actorUserId) {
        ProjectSection section = transition(sectionId, actorUserId,
                ProjectSectionStatus.COLLECTING, "COLLECT_REOPENED");
        if (sectionDraftRepository.existsByProjectSection_Id(sectionId)) {
            section.markSynthesisStale();
        }
        return section;
    }

    /**
     * 공통 전이 처리: 섹션 조회 → 팀장 권한 검증 → 상태 전이(규칙 검증) → 이력 기록.
     */
    private ProjectSection transition(Long sectionId, Long actorUserId,
                                      ProjectSectionStatus target, String eventType) {
        ProjectSection section = projectSectionRepository.findById(sectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SECTION_NOT_FOUND));

        ProjectMember actor = requireOwner(section, actorUserId);

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
