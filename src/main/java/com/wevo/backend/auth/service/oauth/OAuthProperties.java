package com.wevo.backend.auth.service.oauth;

import com.wevo.backend.auth.domain.AuthProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 소셜 제공자별 OAuth2 설정 값.
 *
 * <p>비밀 값(client-id/secret)은 {@code application-local.yml} + {@code .env} 로 주입하고,
 * 공개 엔드포인트(token-uri/user-info-uri)는 {@code application.yml} 에 둔다.
 */
@ConfigurationProperties(prefix = "oauth")
public record OAuthProperties(Provider google, Provider kakao) {

    public Provider get(AuthProvider provider) {
        return switch (provider) {
            case GOOGLE -> google;
            case KAKAO -> kakao;
        };
    }

    /**
     * @param clientId     OAuth2 client id
     * @param clientSecret OAuth2 client secret (카카오는 선택)
     * @param tokenUri     인가코드 → 토큰 교환 엔드포인트
     * @param userInfoUri  사용자 정보 조회 엔드포인트
     */
    public record Provider(
            String clientId,
            String clientSecret,
            String tokenUri,
            String userInfoUri
    ) {
    }
}
