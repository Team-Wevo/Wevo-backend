package com.wevo.backend.global.security;

import java.security.Principal;

/**
 * 인증된 사용자를 나타내는 principal.
 *
 * 컨트롤러에서 {@code @AuthenticationPrincipal AuthPrincipal} 로 주입받아
 * 현재 로그인 사용자의 식별자에 접근한다.
 *
 * @param userId 인증된 사용자 식별자
 */
public record AuthPrincipal(Long userId) implements Principal {

    /**
     * HTTP와 WebSocket 사용자 식별에 공통으로 사용할 안정적인 principal 이름을 반환한다.
     * WebSocket의 `/user/queue` 라우팅에서는 이 값으로 연결된 사용자를 구분한다.
     */
    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
