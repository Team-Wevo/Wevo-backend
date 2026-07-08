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
        Map<String, Object> userInfo = requestUserInfo(accessToken);
        return new OAuthUserInfo(
                AuthProvider.GOOGLE,
                String.valueOf(userInfo.get("sub")),
                (String) userInfo.get("email"),
                (String) userInfo.get("name"),
                (String) userInfo.get("picture")
        );
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
