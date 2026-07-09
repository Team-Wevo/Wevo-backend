package com.wevo.backend.auth.service.oauth;

import com.wevo.backend.auth.domain.AuthProvider;
import org.springframework.core.ParameterizedTypeReference;

import java.util.Map;

/**
 * 소셜 제공자별 OAuth2 연동 클라이언트.
 *
 * 프론트엔드가 전달한 인가코드로 토큰을 교환하고 사용자 정보를 조회한다.
 */
public interface OAuthClient {

    ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {
    };

    /** 이 클라이언트가 담당하는 제공자. */
    AuthProvider provider();

    /**
     * 인가코드로 토큰을 교환하고 사용자 정보를 조회한다.
     *
     * @param authorizationCode 프론트엔드가 제공자로부터 발급받은 인가코드
     * @param redirectUri       인가코드 발급 시 사용한 redirect URI (교환 시 동일해야 함)
     * @return 조회한 사용자 프로필
     */
    OAuthUserInfo fetchUserInfo(String authorizationCode, String redirectUri);
}
