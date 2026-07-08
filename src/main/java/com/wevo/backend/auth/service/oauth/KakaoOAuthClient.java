package com.wevo.backend.auth.service.oauth;

import com.wevo.backend.auth.domain.AuthProvider;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Kakao OAuth2 연동 클라이언트.
 *
 * <a href="https://developers.kakao.com/docs/latest/ko/kakaologin/rest-api">Kakao Login REST API</a>
 *
 * <p>카카오는 이메일을 제공하지 않을 수 있어 {@code email} 이 {@code null} 일 수 있다.
 */
@Component
public class KakaoOAuthClient implements OAuthClient {

    private final OAuthProperties.Provider config;
    private final RestClient restClient;

    public KakaoOAuthClient(OAuthProperties properties) {
        this.config = properties.kakao();
        this.restClient = RestClient.create();
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.KAKAO;
    }

    @Override
    public OAuthUserInfo fetchUserInfo(String authorizationCode, String redirectUri) {
        String accessToken = requestAccessToken(authorizationCode, redirectUri);
        Map<String, Object> userInfo = requestUserInfo(accessToken);
        return mapToUserInfo(userInfo);
    }

    private String requestAccessToken(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", config.clientId());
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        if (StringUtils.hasText(config.clientSecret())) {
            form.add("client_secret", config.clientSecret());
        }

        try {
            Map<String, Object> response = restClient.post()
                    .uri(config.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(MAP_TYPE);
            if (response == null || response.get("access_token") == null) {
                throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
            }
            return (String) response.get("access_token");
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
        }
    }

    private Map<String, Object> requestUserInfo(String accessToken) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri(config.userInfoUri())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(MAP_TYPE);
            if (response == null || response.get("id") == null) {
                throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
            }
            return response;
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
        }
    }

    @SuppressWarnings("unchecked")
    OAuthUserInfo mapToUserInfo(Map<String, Object> userInfo) {
        String providerUserId = String.valueOf(userInfo.get("id"));

        Map<String, Object> kakaoAccount =
                (Map<String, Object>) userInfo.getOrDefault("kakao_account", Map.of());
        Map<String, Object> profile =
                (Map<String, Object>) kakaoAccount.getOrDefault("profile", Map.of());

        String email = (String) kakaoAccount.get("email");
        String name = (String) profile.get("nickname");
        String profileImageUrl = (String) profile.get("profile_image_url");

        return new OAuthUserInfo(AuthProvider.KAKAO, providerUserId, email, name, profileImageUrl);
    }
}
