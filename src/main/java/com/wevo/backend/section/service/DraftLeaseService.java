package com.wevo.backend.section.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.section.domain.DraftLease;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.dto.response.DraftLeaseAcquireResponse;
import com.wevo.backend.section.dto.response.DraftLeaseRenewResponse;
import com.wevo.backend.section.dto.response.DraftLeaseStatusResponse;
import com.wevo.backend.section.repository.DraftLeaseRepository;
import com.wevo.backend.section.repository.SectionDraftRepository;
import com.wevo.backend.user.service.UserService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 섹션 초안 편집 잠금 로직.
 *
 * <p>획득 전 섹션 행을 배타 잠금으로 조회해, 같은 섹션에 동시에 들어온 요청을 직렬화한다.
 * 따라서 "lease 없음 확인 → 생성" 경합에서도 활성 lease가 둘 생기지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class DraftLeaseService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Duration LEASE_DURATION = Duration.ofMinutes(5);
    private static final Set<ProjectSectionStatus> EDITABLE_STATUSES = Set.of(
            ProjectSectionStatus.DRAFTING,
            ProjectSectionStatus.REVIEWING,
            ProjectSectionStatus.CONFIRMED
    );

    private final SectionAccessGuard sectionAccessGuard;
    private final DraftLeaseRepository draftLeaseRepository;
    private final SectionDraftRepository sectionDraftRepository;
    private final UserService userService;

    public DraftLeaseService(SectionAccessGuard sectionAccessGuard,
                             DraftLeaseRepository draftLeaseRepository,
                             SectionDraftRepository sectionDraftRepository,
                             UserService userService) {
        this.sectionAccessGuard = sectionAccessGuard;
        this.draftLeaseRepository = draftLeaseRepository;
        this.sectionDraftRepository = sectionDraftRepository;
        this.userService = userService;
    }

    /**
     * 편집 잠금을 획득한다.
     *
     * <p>프로젝트 참여자(OWNER 또는 MEMBER) 모두 획득할 수 있다. 활성 lease가 없거나 만료됐으면
     * 요청자에게 5분 lease를 부여하고, 본인이 이미 보유 중이면 만료 시각만 연장한다.
     * 초안이 없거나 편집 불가 상태면 각각 {@code S003}, {@code S002}로 거부하고,
     * 다른 사용자가 보유한 활성 lease는 {@code S004} 충돌로 거부한다.
     */
    @Transactional
    public DraftLeaseAcquireResponse acquire(Long projectSectionId, Long userId) {
        ProjectSection section =
                sectionAccessGuard.requireParticipantSectionForUpdate(projectSectionId, userId);
        if (!sectionDraftRepository.existsByProjectSection_Id(projectSectionId)) {
            throw new BusinessException(ErrorCode.SECTION_DRAFT_NOT_FOUND);
        }
        if (!EDITABLE_STATUSES.contains(section.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_SECTION_STATUS_TRANSITION);
        }

        LocalDateTime now = LocalDateTime.now(KST);
        LocalDateTime leaseUntil = now.plus(LEASE_DURATION);
        Optional<DraftLease> existingLease =
                draftLeaseRepository.findByProjectSectionIdForUpdate(projectSectionId);

        DraftLease lease;
        if (existingLease.isEmpty()) {
            lease = DraftLease.builder()
                    .projectSection(section)
                    .holderUserId(userId)
                    .leaseUntil(leaseUntil)
                    .build();
            lease = draftLeaseRepository.saveAndFlush(lease);
        } else {
            lease = existingLease.get();
            if (lease.isActiveAt(now) && !lease.getHolderUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.DRAFT_LEASE_HELD_BY_OTHER);
            }
            lease.grantTo(userId, leaseUntil);
        }

        return DraftLeaseAcquireResponse.from(lease);
    }

    /**
     * 프로젝트 멤버가 현재 보유 중인 유효한 편집 잠금을 5분 연장한다.
     *
     * <p>섹션 접근 권한을 먼저 검사해 비멤버에게 섹션과 lease의 존재를 숨긴다. 활성 lease를
     * 타인이 보유하면 {@code S004}, lease가 없거나 만료됐으면 {@code S005}를 반환한다.
     */
    @Transactional
    public DraftLeaseRenewResponse renew(Long projectSectionId, Long userId) {
        HeldLease heldLease = requireActiveLeaseHeldBy(projectSectionId, userId);

        heldLease.lease().renewUntil(heldLease.checkedAt().plus(LEASE_DURATION));
        return DraftLeaseRenewResponse.from(heldLease.lease());
    }

    /**
     * 프로젝트 멤버가 현재 보유 중인 편집 잠금을 해제한다.
     *
     * <p>섹션 접근 권한을 먼저 검사해 비멤버에게 섹션과 lease의 존재를 숨긴다. 활성 lease를
     * 타인이 보유하면 {@code S004}(강제 해제 불가), lease가 없거나 만료됐으면 {@code S005}를
     * 반환한다. 해제는 만료 시각을 현재로 당겨 비활성으로 만들 뿐 행을 삭제하지 않는다
     * (다음 획득 요청이 재사용하는 기존 모델 유지 — {@link DraftLease#release}).
     */
    @Transactional
    public void release(Long projectSectionId, Long userId) {
        HeldLease heldLease = requireActiveLeaseHeldBy(projectSectionId, userId);

        heldLease.lease().release(heldLease.checkedAt());
    }

    /**
     * 상태 전이 전에 활성 편집 잠금을 정리한다.
     *
     * <p>호출자가 보유한 활성 lease는 해제하고, 타인이 보유 중이면 {@code S004}로 전이를
     * 거부한다. lease가 없거나 이미 만료됐으면 전이를 막지 않는다. 호출자는 이 메서드보다 먼저
     * 섹션 접근 권한을 검증하고 섹션 행을 배타 잠금으로 획득해야 한다. 그래야 lease 획득과 상태
     * 전이가 같은 잠금 순서로 직렬화된다.
     */
    @Transactional
    public void releaseOwnLeaseOrRejectOther(Long projectSectionId, Long userId) {
        Optional<DraftLease> lease =
                draftLeaseRepository.findByProjectSectionIdForUpdate(projectSectionId);
        if (lease.isEmpty()) {
            return;
        }

        // lease 행의 배타 잠금을 획득한 뒤 만료 여부를 판정한다.
        LocalDateTime now = LocalDateTime.now(KST);
        if (!lease.get().isActiveAt(now)) {
            return;
        }
        if (!lease.get().getHolderUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.DRAFT_LEASE_HELD_BY_OTHER);
        }
        lease.get().release(now);
    }

    /**
     * 섹션의 현재 편집 잠금 상태를 조회한다.
     *
     * <p>유효한 lease가 있을 때만 {@code locked=true}다. lease가 없거나 만료됐으면
     * 404가 아니라 {@code locked=false}를 반환한다. 조회 과정에서 만료 행을
     * 삭제하지 않으며, 다음 획득 요청이 해당 행을 재사용한다.
     */
    public DraftLeaseStatusResponse getStatus(Long projectSectionId, Long userId) {
        sectionAccessGuard.requireParticipantSection(projectSectionId, userId);

        return findActiveLease(projectSectionId)
                .map(lease -> DraftLeaseStatusResponse.locked(
                        lease.getHolderUserId(),
                        userService.getUserName(lease.getHolderUserId()),
                        lease.getLeaseUntil()
                ))
                .orElseGet(DraftLeaseStatusResponse::unlocked);
    }

    /**
     * 섹션의 유효한 편집 잠금을 반환한다. 없거나 만료됐으면 {@link Optional#empty()}.
     *
     * <p><b>권한 검사를 하지 않는다</b> — 접근 권한을 이미 확인한 같은 도메인의 호출자가
     * "누가 편집 중인가"만 필요할 때 쓴다(초안 조회의 {@code activeEditor}). 권한 검사가 필요한
     * 외부 진입점은 {@link #getStatus} 를 사용한다.
     */
    public Optional<DraftLease> findActiveLease(Long projectSectionId) {
        LocalDateTime now = LocalDateTime.now(KST);

        return draftLeaseRepository.findByProjectSection_Id(projectSectionId)
                .filter(lease -> lease.isActiveAt(now));
    }

    /**
     * 호출자가 섹션의 유효한 편집권 보유자인지 검증한다 — 초안 저장 등 편집 행위의 사전 조건. (§5.2.1)
     *
     * <p>lease 행을 배타 잠금으로 조회해 acquire·renew·해제와 직렬화한다. 활성 lease가 없거나
     * 만료됐으면 {@code S005}(미보유), 타인이 보유하면 {@code S004}(충돌)로 거부한다. 상태를
     * 바꾸지 않는 검증 전용이라, 잠금을 저장과 같은 트랜잭션에서 유지하려면 쓰기 트랜잭션 안에서
     * 호출한다(호출자의 트랜잭션에 참여한다).
     *
     * @throws BusinessException 편집권 미보유·만료({@code S005}), 타인이 편집 중({@code S004})
     */
    public void requireActiveHolder(Long projectSectionId, Long userId) {
        LocalDateTime now = LocalDateTime.now(KST);
        DraftLease lease = draftLeaseRepository.findByProjectSectionIdForUpdate(projectSectionId)
                .filter(existing -> existing.isActiveAt(now))
                .orElseThrow(() -> new BusinessException(ErrorCode.DRAFT_LEASE_NOT_HELD));
        if (!lease.getHolderUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.DRAFT_LEASE_HELD_BY_OTHER);
        }
    }

    /**
     * 저장·편집 종료로 호출자가 보유한 편집권을 해제한다. (§5.2.1 "저장하거나 편집을 종료하면 해제")
     *
     * <p>lease 행을 배타 잠금으로 조회해 acquire·renew·검사와 직렬화한다. 만료 행은 삭제하지 않고
     * 만료 시각을 현재로 당겨 비활성으로 만들어, 다음 획득 요청이 재사용한다. 호출자가 보유한
     * 활성 lease가 없으면(이미 만료·해제·타인 보유) 아무 것도 하지 않는다 — 멱등하다.
     *
     * <p>검증은 하지 않는다 — 호출측이 {@link #requireActiveHolder}로 보유를 확인한 뒤,
     * 같은 트랜잭션에서 저장이 성공했을 때만 호출하는 것을 전제로 한다.
     */
    public void releaseHeldBy(Long projectSectionId, Long userId) {
        LocalDateTime now = LocalDateTime.now(KST);
        draftLeaseRepository.findByProjectSectionIdForUpdate(projectSectionId)
                .filter(lease -> lease.isActiveAt(now) && lease.getHolderUserId().equals(userId))
                .ifPresent(lease -> lease.release(now));
    }

    /**
     * 한 사용자가 보유한 <b>모든</b> 활성 lease를 즉시 해제한다. (회원 탈퇴 정리용)
     *
     * <p>탈퇴 직후에도 Access Token 이 30분간 살아 있어, 탈퇴자가 renew 를 계속 돌리면 그 시간 동안
     * 팀원 전원의 편집이 {@code S004} 로 막힌다. 탈퇴 시점에 lease 를 비워 두면 다른 멤버가 즉시
     * 획득할 수 있고, 탈퇴자의 renew 는 보유 lease 가 없어 {@code S005} 로 자연히 실패한다.
     *
     * <p>{@code holderUserId} 로 여러 섹션의 lease 를 배타 잠금으로 조회해 acquire·renew 와
     * 직렬화한다. 정리 사이에 탈퇴자가 새 lease 를 잡는 경합은 탈퇴가 소셜 연결·토큰까지 함께 끊어
     * 곧 닫히므로, 여기서는 지금 쥔 것만 해제한다.
     */
    @Transactional
    public void releaseAllHeldBy(Long userId) {
        LocalDateTime now = LocalDateTime.now(KST);
        draftLeaseRepository.findActiveByHolderForUpdate(userId, now)
                .forEach(lease -> lease.release(now));
    }

    /**
     * 참여자 권한을 확인한 뒤, 호출자가 보유한 활성 lease를 배타 잠금으로 반환한다.
     * renew와 release가 동일한 검증 순서와 오류 계약을 사용하도록 한 곳에서 관리한다.
     */
    private HeldLease requireActiveLeaseHeldBy(Long projectSectionId, Long userId) {
        sectionAccessGuard.requireParticipantSectionForUpdate(projectSectionId, userId);
        DraftLease lease = draftLeaseRepository.findByProjectSectionIdForUpdate(projectSectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DRAFT_LEASE_NOT_HELD));

        // 잠금 대기로 시간이 흐를 수 있으므로 두 배타 잠금을 모두 획득한 뒤 만료 판정 시각을 만든다.
        LocalDateTime now = LocalDateTime.now(KST);
        if (!lease.isActiveAt(now)) {
            throw new BusinessException(ErrorCode.DRAFT_LEASE_NOT_HELD);
        }
        if (!lease.getHolderUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.DRAFT_LEASE_HELD_BY_OTHER);
        }
        return new HeldLease(lease, now);
    }

    private record HeldLease(DraftLease lease, LocalDateTime checkedAt) {
    }
}
