package com.wevo.backend.auth.service;

import com.wevo.backend.auth.repository.AuthAccountRepository;
import com.wevo.backend.user.domain.UserWithdrawnEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원이 탈퇴하면 auth 도메인이 가진 인증 흔적을 지운다. (API_SPEC §3.3.3)
 *
 * <p>user 도메인이 {@code auth_accounts} 와 Redis 를 직접 만지지 않도록 이벤트로 갈라 둔 자리다.
 * (CLAUDE.md §6 — 도메인 간 접근) auth 는 이미 user 를 참조하므로 반대 방향 참조를 만들면 순환이 된다.
 *
 * <p>{@code @EventListener} 라 발행 시점에 <b>같은 스레드·같은 트랜잭션</b>에서 실행된다. 여기서
 * 실패하면 탈퇴 자체가 롤백되는데, 소셜 연결이 살아 있는 채로 탈퇴만 성사되는 것보다 그편이 안전하다
 * (그 상태로 재로그인하면 지워진 계정에 그대로 붙는다).
 */
@Service
public class WithdrawnUserAuthCleaner {

    private final AuthAccountRepository authAccountRepository;
    private final RefreshTokenService refreshTokenService;

    public WithdrawnUserAuthCleaner(AuthAccountRepository authAccountRepository,
                                    RefreshTokenService refreshTokenService) {
        this.authAccountRepository = authAccountRepository;
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * 소셜 연결을 끊고 Refresh Token 을 폐기한다.
     *
     * <p>Refresh Token 폐기가 이 이슈에서 <b>실질적인 접근 차단</b>을 맡는다. Access Token 은 서버가
     * 회수할 수 없어 남은 유효 시간(30분) 동안 살아 있지만, 재발급 경로가 막히므로 그 이상은 늘어나지
     * 않는다. Redis 삭제는 트랜잭션 대상이 아니라 롤백돼도 되돌아오지 않는데, 토큰이 사라지는 방향은
     * 안전한 쪽이라 그대로 둔다.
     */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(UserWithdrawnEvent event) {
        authAccountRepository.deleteAllByUserId(event.userId());
        refreshTokenService.delete(event.userId());
    }
}
