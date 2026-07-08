package com.wevo.backend.global.security;

/**
 * 인증된 사용자를 나타내는 principal.
 *
 * 컨트롤러에서 {@code @AuthenticationPrincipal AuthPrincipal} 로 주입받아
 * 현재 로그인 사용자의 식별자에 접근한다.
 *
 * @param userId 인증된 사용자 식별자
 */
public record AuthPrincipal(Long userId) {
}
