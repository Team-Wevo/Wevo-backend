package com.wevo.backend.auth.service.oauth;

import com.wevo.backend.auth.domain.AuthProvider;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Google OAuth2 연동 클라이언트.
 *
 * <a href="https://developers.google.com/identity/protocols/oauth2">Google OAuth2</a>
 */
@Component
public class GoogleOAuthClient implements OAuthClient {

    private final OAuthProperties.Provider config;
    private final RestClient restClient;

    public GoogleOAuthClient(OAuthProperties properties) {
        this.config = properties.google();
        this.restClient = OAuthRestClientFactory.create(properties.timeout());
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.GOOGLE;
    }

    @Override
    public OAuthUserInfo fetchUserInfo(String authorizationCode, String redirectUri) {
        String accessToken = requestAccessToken(authorizationCode, redirectUri);
        return mapToUserInfo(requestUserInfo(accessToken));
    }

    /** 응답 본문 매핑만 분리한다 — HTTP 호출 없이 검증할 수 있게. ({@code KakaoOAuthClient} 와 동일 구조) */
    OAuthUserInfo mapToUserInfo(Map<String, Object> userInfo) {
        return new OAuthUserInfo(
                AuthProvider.GOOGLE,
                String.valueOf(userInfo.get("sub")),
                verifiedEmail(userInfo),
                (String) userInfo.get("name"),
                (String) userInfo.get("picture")
        );
    }

    /**
     * 소유가 확인된 이메일만 돌려준다. 확인되지 않았으면 {@code null} — 제공자가 이메일을 주지
     * 않은 경우와 같게 다룬다.
     *
     * <p>이메일은 <b>중복 가입 차단</b>에 쓰인다. 검증하지 않은 값을 그대로 믿으면, 타인의 이메일을
     * 적어 둔 계정으로 먼저 가입해 실소유자의 이후 가입을 영구히 막는 선점이 가능하다. 계정 식별은
     * {@code provider + sub} 로 하므로 이메일을 비워도 로그인 자체에는 지장이 없다.
     */
    private String verifiedEmail(Map<String, Object> userInfo) {
        return Boolean.TRUE.equals(userInfo.get("email_verified"))
                ? (String) userInfo.get("email")
                : null;
    }

    private String requestAccessToken(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", config.clientId());
        form.add("client_secret", config.clientSecret());
        form.add("code", code);
        form.add("redirect_uri", redirectUri);

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
            if (response == null || response.get("sub") == null) {
                throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
            }
            return response;
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
        }
    }
}
