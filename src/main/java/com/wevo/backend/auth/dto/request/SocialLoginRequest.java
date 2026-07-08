package com.wevo.backend.auth.dto.request;

import com.wevo.backend.auth.domain.AuthProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 소셜 로그인 요청.
 *
 * @param provider    소셜 제공자 (GOOGLE/KAKAO)
 * @param code        프론트엔드가 제공자로부터 발급받은 인가코드
 * @param redirectUri 인가코드 발급에 사용한 redirect URI (토큰 교환 시 동일해야 함)
 */
public record SocialLoginRequest(
        @NotNull AuthProvider provider,
        @NotBlank String code,
        @NotBlank String redirectUri
) {
}
