package com.wevo.backend.section.service;

import com.wevo.backend.user.domain.UserWithdrawnEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원이 탈퇴하면 그 사용자가 쥐고 있던 초안 편집 잠금(lease)을 모두 해제한다. (#217)
 *
 * <p>lease 는 {@code section} 도메인의 것이라, user 도메인이 {@code DraftLeaseRepository} 를 직접
 * 만지지 않도록 이벤트로 갈라 둔다. (CLAUDE.md §6) user → section 의존 방향만 남는다.
 *
 * <p>{@code @EventListener} 라 발행 시점에 <b>같은 스레드·같은 트랜잭션</b>에서 실행된다. lease 해제가
 * 실패하면 탈퇴도 함께 롤백되는 편이, lease 가 남은 채 탈퇴만 성사돼 팀 편집이 막히는 것보다 안전하다.
 * ({@code WithdrawnUserAuthCleaner} 의 소셜 연결·토큰 정리와 같은 트랜잭션·같은 원칙)
 */
@Service
public class WithdrawnUserLeaseCleaner {

    private final DraftLeaseService draftLeaseService;

    public WithdrawnUserLeaseCleaner(DraftLeaseService draftLeaseService) {
        this.draftLeaseService = draftLeaseService;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(UserWithdrawnEvent event) {
        draftLeaseService.releaseAllHeldBy(event.userId());
    }
}
