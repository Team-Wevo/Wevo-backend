package com.wevo.backend.user.dto.response;

import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;

/**
 * 내 프로필 응답. 로그인 응답이 토큰만 주므로, 프론트가 프로필 표시·세션 복원에 사용한다.
 *
 * @param email           소셜 계정에서 받은 이메일 (없을 수 있음 — nullable)
 * @param profileImageUrl provider가 준 프로필 이미지 URL (nullable)
 */
public record MyProfileResponse(
        Long userId,
        String name,
        String email,
        String profileImageUrl,
        UserStatus status
) {

    public static MyProfileResponse from(User user) {
        return new MyProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getProfileImageUrl(),
                user.getStatus()
        );
    }
}
