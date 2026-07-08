package com.wevo.backend.auth.service.oauth;

import com.wevo.backend.auth.domain.AuthProvider;

/**
 * 소셜 제공자로부터 조회한 사용자 프로필.
 *
 * <p>계정 식별은 {@code provider + providerUserId} 로 하며, 이메일은 이메일 식별용이 아니라
 * 제공자가 주면 저장하는 부가 정보다. (카카오는 제공하지 않을 수 있어 {@code null} 가능)
 *
 * @param provider        소셜 제공자
 * @param providerUserId  제공자가 부여한 고유 사용자 ID (이메일 아님)
 * @param email           이메일 (nullable)
 * @param name            표시 이름 (제공자 닉네임/이름)
 * @param profileImageUrl 프로필 이미지 URL (nullable)
 */
public record OAuthUserInfo(
        AuthProvider provider,
        String providerUserId,
        String email,
        String name,
        String profileImageUrl
) {
}
