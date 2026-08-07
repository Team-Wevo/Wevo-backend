package com.wevo.backend.user.domain;

/**
 * 회원이 탈퇴했음을 알리는 도메인 이벤트.
 *
 * <p>탈퇴에 딸린 <b>부수 정리</b>를 user 도메인이 직접 하지 않기 위해 쓴다. 소셜 연결
 * ({@code auth_accounts})과 Refresh Token 은 auth 도메인의 것이라, user 가 그 리포지토리를
 * 직접 만지면 도메인 경계를 넘고(CLAUDE.md §6) 의존 방향도 user → auth → user 로 순환한다.
 *
 * <p>차단 조건인 OWNER 검사는 이벤트가 아니라 {@link com.wevo.backend.user.service.ProjectOwnershipQuery}
 * 로 동기 조회한다 — 탈퇴를 <b>막아야</b> 하므로 결과를 기다려야 하고, 이벤트로는 막을 수 없다.
 *
 * <p>수신자는 기본 {@code @EventListener} 로 같은 트랜잭션에서 동기 처리한다. 정리가 실패하면
 * 탈퇴도 함께 롤백되는 편이, 소셜 연결이 남은 채로 탈퇴만 되는 것보다 안전하다.
 */
public record UserWithdrawnEvent(Long userId) {
}
