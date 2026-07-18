package com.wevo.backend.section.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.response.FieldError;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.section.domain.DraftLease;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.dto.response.DraftLeaseAcquireResponse;
import com.wevo.backend.section.repository.DraftLeaseRepository;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.service.UserService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 섹션 초안 편집 잠금 획득 로직.
 *
 * <p>획득 전 섹션 행을 배타 잠금으로 조회해, 같은 섹션에 동시에 들어온 요청을 직렬화한다.
 * 따라서 "lease 없음 확인 → 생성" 경합에서도 활성 lease가 둘 생기지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class DraftLeaseService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Duration LEASE_DURATION = Duration.ofSeconds(60);

    private final SectionAccessGuard sectionAccessGuard;
    private final DraftLeaseRepository draftLeaseRepository;
    private final UserService userService;

    public DraftLeaseService(SectionAccessGuard sectionAccessGuard,
                             DraftLeaseRepository draftLeaseRepository,
                             UserService userService) {
        this.sectionAccessGuard = sectionAccessGuard;
        this.draftLeaseRepository = draftLeaseRepository;
        this.userService = userService;
    }

    /**
     * 편집 잠금을 획득한다.
     *
     * <p>프로젝트 참여자(OWNER 또는 MEMBER) 모두 획득할 수 있다. 활성 lease가 없거나 만료됐으면
     * 요청자에게 60초 lease를 부여하고, 본인이 이미 보유 중이면 만료 시각만 연장한다.
     * 다른 사용자가 보유한 활성 lease는 {@code S005} 충돌로 거부한다.
     */
    @Transactional
    public DraftLeaseAcquireResponse acquire(Long projectSectionId, Long userId) {
        ProjectSection section =
                sectionAccessGuard.requireParticipantSectionForUpdate(projectSectionId, userId);
        LocalDateTime now = LocalDateTime.now(KST);
        LocalDateTime leaseUntil = now.plus(LEASE_DURATION);
        Optional<DraftLease> existingLease =
                draftLeaseRepository.findByProjectSection_Id(projectSectionId);

        DraftLease lease;
        if (existingLease.isEmpty()) {
            lease = DraftLease.builder()
                    .projectSection(section)
                    .holder(userService.getUserReference(userId))
                    .leaseUntil(leaseUntil)
                    .build();
            lease = draftLeaseRepository.saveAndFlush(lease);
        } else {
            lease = existingLease.get();
            if (lease.isActiveAt(now) && !lease.getHolder().getId().equals(userId)) {
                throw heldByOther(lease);
            }
            lease.grantTo(userService.getUserReference(userId), leaseUntil);
        }

        return DraftLeaseAcquireResponse.from(lease);
    }

    private BusinessException heldByOther(DraftLease lease) {
        String reason = "%s (leaseUntil: %s)".formatted(
                lease.getHolder().getName(),
                lease.getLeaseUntil()
        );
        return new BusinessException(
                ErrorCode.DRAFT_LEASE_HELD_BY_OTHER,
                List.of(new FieldError("holder", reason))
        );
    }
}
